import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DepartementPlacement {

    public static void main(String[] args) {
        try {
            
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.exolab.jms.jndi.InitialContextFactory");
            properties.put(Context.PROVIDER_URL, "tcp://localhost:3035");

            InitialContext ctx = new InitialContext(properties);

            
            TopicConnectionFactory connectionFactory = (TopicConnectionFactory) ctx.lookup("CF");
            Topic topic = (Topic) ctx.lookup("topic1");

            
            TopicConnection connection = connectionFactory.createTopicConnection();
            TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            // Utilisation du JMS Message Selector pour filtrer les messages
            String selector = "accountOpenedThisYear = true AND balance > 500000";
            TopicSubscriber subscriber = session.createSubscriber(topic, selector, false);

            connection.start();

            System.out.println("Département des placements démarré et en attente de notifications filtrées...");

           
            subscriber.setMessageListener(message -> {
                try {
                    if (message instanceof ObjectMessage) {
                        ObjectMessage objectMessage = (ObjectMessage) message;
                        Client client = (Client) objectMessage.getObject();
                        System.out.println("Proposition de placement financier à l'usager " +
                                client.getAccountNumber() + " envoyée par courrier électronique.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

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

