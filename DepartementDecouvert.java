import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

public class DepartementDecouvert {

    private static final String TOPIC_NAME = "topic1"; 
    private static final String FILE2_NAME = "file2";   

    public static void main(String[] args) {
        try {
            
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.exolab.jms.jndi.InitialContextFactory");
            properties.put(Context.PROVIDER_URL, "tcp://localhost:3035");

            InitialContext ctx = new InitialContext(properties);

            TopicConnectionFactory connectionFactoryT = (TopicConnectionFactory) ctx.lookup("CF");
            QueueConnectionFactory connectionFactoryQ = (QueueConnectionFactory) ctx.lookup("CF");
            Topic topic = (Topic) ctx.lookup(TOPIC_NAME); 
            Queue queue2 = (Queue) ctx.lookup(FILE2_NAME); 

            
            String selector = "balance < 0";  
            TopicConnection topicConnection = connectionFactoryT.createTopicConnection();
            TopicSession topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber subscriber = topicSession.createSubscriber(topic, selector, false);

            
            QueueConnection queueConnection = connectionFactoryQ.createQueueConnection();
            QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueSender sender = queueSession.createSender(queue2);

            topicConnection.start();
            queueConnection.start();

            System.out.println("Département des découverts en attente de notifications...");

            while (true) {
                
                Message message = subscriber.receive(); 

                if (message instanceof ObjectMessage) {
                    ObjectMessage objMessage = (ObjectMessage) message;
                    Object obj = objMessage.getObject();

                    if (obj instanceof Client) {
                        Client client = (Client) obj;
                        System.out.println("Notification reçue : " + client);
                        //facultatif etant donné que j'ai deja un selector just to be sure :)
                        if (client.getBalance() < 0) {
                            System.out.println("Compte avec solde négatif détecté. ID: " + client.getAccountNumber());

                            MapMessage mapMessage = queueSession.createMapMessage();
                            mapMessage.setInt("accountNumber", client.getAccountNumber());
                            mapMessage.setDouble("amount", 8.0); 
                            sender.send(mapMessage);
                            System.out.println("Message envoyé au gérant pour retirer 8 euros du compte: " + client.getAccountNumber());
                        }
                    } else {
                        System.out.println("L'objet reçu n'est pas de type ClientMessage. Type: " + obj.getClass().getName());
                    }
                } else {
                    System.out.println("Message reçu n'est pas un ObjectMessage. Type: " + message.getClass().getName());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
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
