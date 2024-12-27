import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;
import java.util.Scanner;
import java.time.LocalDateTime;
import java.io.Serializable;

public class ClientCompte {

    public static void main(String[] args) {
        try {
            
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.exolab.jms.jndi.InitialContextFactory");
            properties.put(Context.PROVIDER_URL, "tcp://localhost:3035");

            InitialContext ctx = new InitialContext(properties);

            QueueConnectionFactory connectionFactory = (QueueConnectionFactory) ctx.lookup("CF");
            Queue queue = (Queue) ctx.lookup("file1");

            QueueConnection connection = connectionFactory.createQueueConnection();
            QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueSender sender = session.createSender(queue);

            connection.start();

            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            System.out.println("Bienvenue dans le programme Client de gestion de compte !");
            
            while (running) {
                System.out.println("\nQue souhaitez-vous faire ?");
                System.out.println("1 - Dépôt");
                System.out.println("2 - Retrait");
                System.out.println("3 - Quitter");
                System.out.print("Votre choix : ");

                String choix = scanner.nextLine();

                switch (choix) {
                    case "1":
                        
                        envoyerMessage(session, sender, "deposit", scanner);
                        break;
                    case "2":

                        envoyerMessage(session, sender, "withdraw", scanner);
                        break;
                    case "3":

                        System.out.println("Merci d'avoir choisi notre banque A bientôt <3 !");
                        running = false;
                        break;
                    default:
                        System.out.println("Choix invalide. Veuillez entrer 1, 2 ou 3.");
                        break;
                }
            }

            
            sender.close();
            session.close();
            connection.close();
            ctx.close();
            scanner.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Méthode pour envoyer un message dans la file JMS
     */
    private static void envoyerMessage(QueueSession session, QueueSender sender, String operation, Scanner scanner) {
        try {
            
            System.out.print("Entrez le numéro de compte (entier) : ");
            String numeroCompteStr = scanner.nextLine();
            int numeroCompte;
            try {
                numeroCompte = Integer.parseInt(numeroCompteStr);
            } catch (NumberFormatException e) {
                System.out.println("Erreur : Le numéro de compte doit être un entier.");
                return;
            }

            System.out.print("Entrez votre nom et prénom (format NOM.Prenom) : ");
            String nomPrenom = scanner.nextLine();
            if (!nomPrenom.matches("[A-Za-z]+\\.[A-Za-z]+")) {
                System.out.println("Erreur : Le nom et prénom doivent être au format NOM.Prenom.");
                return;
            }

            System.out.print("Entrez le montant (entier ou décimal) : ");
            String montantStr = scanner.nextLine();
            double montant;
            try {
                montant = Double.parseDouble(montantStr);
                if (montant <= 0) {
                    System.out.println("Erreur : Le montant doit être supérieur à 0.");
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println("Erreur : Le montant doit être un entier ou un décimal.");
                return;
            }

            
            String date = LocalDateTime.now().toString();

            
            ClientMessage messageObj = new ClientMessage(operation, numeroCompte, nomPrenom, montant, date);

            
            ObjectMessage message = session.createObjectMessage(messageObj);
            sender.send(message);

            System.out.println("Message envoyé avec succès : " + messageObj);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * Classe Message
 * Représente un message de dépôt ou de retrait envoyé par le client
 */
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

    @Override
    public String toString() {
        return "Message{" +
                "operation='" + operation + '\'' +
                ", accountNumber=" + accountNumber +
                ", name='" + name + '\'' +
                ", amount=" + amount +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}

