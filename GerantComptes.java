import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class GerantComptes {

    private static Map<Integer, Client> clients = new HashMap<>();
    private static final String TOPIC_NAME = "topic1";
    private static final String FILE1_NAME = "file1";
    private static final String FILE2_NAME = "file2";

    private static TopicSession topicSession;
    private static TopicPublisher publisher;

    public static void main(String[] args) {
        try {
            // Création dynamique des ressources JMS (topics et queues)
            createJmsResources();

            // Logique existante pour initialiser les connexions et gérer les messages
            System.out.println("Les ressources JMS ont été créées avec succès !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Création des ressources JMS dynamiquement (sans l'API d'administration)
     */
    private static void createJmsResources() {
        try {
            // Connexion au contexte JNDI
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.exolab.jms.jndi.InitialContextFactory");
            properties.put(Context.PROVIDER_URL, "tcp://localhost:3035");

            InitialContext ctx = new InitialContext(properties);

            // Récupération des factories de connexions
            QueueConnectionFactory connectionFactory = (QueueConnectionFactory) ctx.lookup("CF");
            TopicConnectionFactory connectionFactoryT = (TopicConnectionFactory) ctx.lookup("CF");

            // Connexion aux queues et topic avec création dynamique
            QueueConnection queueConnection = connectionFactory.createQueueConnection();
            TopicConnection topicConnection = connectionFactoryT.createTopicConnection();

            // Création des sessions
            QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); 
            publisher = topicSession.createPublisher(topicSession.createTopic(TOPIC_NAME));

            // Création dynamique des queues
            Queue queue1 = queueSession.createQueue(FILE1_NAME);  // Création dynamique de la Queue1
            Queue queue2 = queueSession.createQueue(FILE2_NAME);  // Création dynamique de la Queue2
            System.out.println("Queue '" + FILE1_NAME + "' et '" + FILE2_NAME + "' créées dynamiquement.");

            // Rebind des queues dans le contexte JNDI (si elles existent déjà)
            try {
                ctx.rebind(FILE1_NAME, queue1);  // Lier ou mettre à jour la queue1 dans le contexte JNDI
                ctx.rebind(FILE2_NAME, queue2);  // Lier ou mettre à jour la queue2 dans le contexte JNDI
            } catch (Exception e) {
                System.err.println("Erreur lors de la liaison des queues au contexte JNDI : " + e.getMessage());
                e.printStackTrace();
            }

            // Création dynamique du topic
            Topic topic = topicSession.createTopic(TOPIC_NAME);  // Création dynamique du Topic
            System.out.println("Topic '" + TOPIC_NAME + "' créé dynamiquement.");

            // Lier le topic au contexte JNDI (en utilisant rebind)
            try {
                ctx.rebind(TOPIC_NAME, topic);  // Lier ou mettre à jour le topic dans le contexte JNDI
                System.out.println("Topic '" + TOPIC_NAME + "' lié au contexte JNDI.");
            } catch (Exception e) {
                System.err.println("Erreur lors de la liaison du topic au contexte JNDI : " + e.getMessage());
                e.printStackTrace();
            }

            // Création des receivers pour les queues
            QueueReceiver receiver1 = queueSession.createReceiver(queue1); 
            QueueReceiver receiver2 = queueSession.createReceiver(queue2); 

            // Démarrage des connexions
            queueConnection.start();
            topicConnection.start();  

            System.out.println("Le gérant des comptes est maintenant en attente de messages...");

            // Thread pour recevoir les messages de Queue1
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = receiver1.receive(); 
                
                        if (message instanceof ObjectMessage) {
                            System.out.println("Message reçu est un ObjectMessage. Type reçu: " + message.getClass().getName());
                
                            ObjectMessage objMessage = (ObjectMessage) message; 
                            Object obj = objMessage.getObject(); 
                
                            if (obj instanceof ClientMessage) { 
                                System.out.println("L'objet reçu est une instance de ClientMessage");
                                ClientMessage clientMessage = (ClientMessage) obj; 
                                traiterDemande(clientMessage);
                                publierNotification(clientMessage.getAccountNumber());  
                                System.out.println("Liste des clients enregistrés :");
                                clients.values().forEach(System.out::println);
                            } else {
                                System.out.println("L'objet reçu n'est pas du type ClientMessage. Type: " + obj.getClass().getName());
                            }
                        } else {
                            System.out.println("Message reçu n'est pas un ObjectMessage. Type reçu: " + message.getClass().getName());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Listener pour Queue2
            receiver2.setMessageListener(message -> {
                try {
                    if (message instanceof MapMessage) {
                        MapMessage mapMessage = (MapMessage) message;
                        int accountNumber = mapMessage.getInt("accountNumber");
                        double amount = mapMessage.getDouble("amount");
                        System.out.println("Retrait de " + amount + " appliqué pour le compte : " + accountNumber);
                        appliquerRetraitDecouvert(accountNumber, amount);
                    } else {
                        System.out.println("Message reçu n'est pas un MapMessage. Type reçu : " + message.getClass().getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void traiterDemande(ClientMessage clientMessage) {
        Client client = clients.get(clientMessage.getAccountNumber());
        if (client == null) {
            client = new Client(clientMessage.getAccountNumber(), clientMessage.getName(), clientMessage.getTimestamp());
            clients.put(clientMessage.getAccountNumber(), client);
        }

        if (clientMessage.getOperation().equals("deposit")) {
            client.deposit(clientMessage.getAmount());
        } else if (clientMessage.getOperation().equals("withdraw")) {
            client.withdraw(clientMessage.getAmount());
        }
    }

    private static void publierNotification(int accountNumber) throws JMSException {
        Client client = clients.get(accountNumber);
        if (client != null) {
            ObjectMessage message = topicSession.createObjectMessage();
            message.setObject(client);  
            message.setDoubleProperty("balance", client.getBalance());
            message.setBooleanProperty("accountOpenedThisYear", LocalDate.parse(client.getOpeningDate(), DateTimeFormatter.ISO_LOCAL_DATE_TIME).getYear() == LocalDate.now().getYear());
            publisher.publish(message);
            System.out.println("Notification publiée pour le client : " + client);
        } else {
            System.out.println("Erreur : Aucun client trouvé avec le numéro de compte " + accountNumber);
        }
    }

    private static void appliquerRetraitDecouvert(int accountNumber, double amount) throws JMSException {
        Client client = clients.get(accountNumber);
        if (client != null) {
            client.withdraw(amount);
            System.out.println("Nouveau solde : " + client.getBalance());
        } else {
            System.out.println("Compte introuvable pour le numéro : " + accountNumber);
        }
    }
}


class Client implements Serializable {
    private static final long serialVersionUID = 1L;

    private int accountNumber;         
    private String name;              
    private double balance;            
    private List<String> operations;   
    private String openingDate;       

    public Client(int accountNumber, String name, String openingDate) {
        this.accountNumber = accountNumber;
        this.name = name;
        this.balance = 0.0;  
        this.operations = new ArrayList<>();
        this.openingDate = openingDate;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public List<String> getOperations() {
        return operations;
    }

    public String getOpeningDate() {
        return openingDate;
    }

    public void deposit(double amount) {
        this.balance += amount;
        operations.add("Dépôt: " + amount + " | Nouveau solde: " + balance);
    }

    public void withdraw(double amount) {
        this.balance -= amount;
        operations.add("Retrait: " + amount + " | Nouveau solde: " + balance);
    }

    @Override
    public String toString() {
        return "Client{" +
                "accountNumber=" + accountNumber +
                ", name='" + name + '\'' +
                ", balance=" + balance +
                ", openingDate='" + openingDate + '\'' +
                '}';
    }
}


class ClientMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String operation;     
    private int accountNumber;    
    private String name;          
    private double amount;        
    private String timestamp;     

    public ClientMessage(String operation, int accountNumber, String name, double amount, String timestamp) {
        this.operation = operation;
        this.accountNumber = accountNumber;
        this.name = name;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getName() {
        return name;
    }

    public double getAmount() {
        return amount;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
