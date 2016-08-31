import java.io.*;
import java.net.*;
/**
 * Этот класс реализует простой однопоточный проксисервер.
 **/
public class SimpleProxyServer {
    /** Метод main() анализирует аргументы и передает их методу runServer */
    public static void main(String[] args) throws IOException {
        try {
            // Проверяем число аргументов
            if (args.length != 3)
                throw new IllegalArgumentException("Неправильное число аргументов.");
            // Получаем заданные в командной строке аргументы: узел и порт, которые мы
            // представляем, а также локальный порт, по которому мы ожидаем подключений.
            String host = args[0];
            int remoteport = Integer.parseInt(args[1]);
            int localport = Integer.parseInt(args[2]);
            // Печатаем стартовое сообщение
            System.out.println("Запускаем прокси для " + host + ":" +
                    remoteport + " по порту " + localport);
            // И запускаем сервер
            runServer(host, remoteport, localport); // Исполнение никогда не прекращается
        }
        catch (Exception e) {
            System.err.println(e);
            System.err.println("Формат: java SimpleProxyServer " +
                    "<host> <remoteport> <localport>");
        }
    }
    /**
     * Этот метод запускает однопоточный проксисервер для host:remoteport
     * на заданном локальном порте. Он никогда не прекращает работу.
     **/
    public static void runServer(String host, int remoteport, int localport)
            throws IOException {
        // Создаем ServerSocket, ожидающий подключений к нему
        ServerSocket ss = new ServerSocket(localport);
        // Создаем буферы для передачи от клиента к серверу и от сервера к клиенту.
        // Один из них объявлен как final, поэтому он может использоваться
        // в приведенном ниже безымянном классе. Обратите внимание на предположения
        // относительно объема передачи в каждом направлении.
        final byte[] request = new byte[1024];
        byte[] reply = new byte[4096];
        // Это сервер, который никогда не прекращает работу, так что входим
        // в бесконечный цикл.
        while(true) {
            // Переменные, в которых будут храниться объекты Socket
            // для соединения с клиентом и с сервером.
            Socket client = null, server = null;
            try {
                // Ожидаем подключения к локальному порту,
                client = ss.accept();
                // Получаем потоки для связи с клиентом. Определяем их как final, так что
                // ими можно будет пользоваться в приведенном ниже безымянном классе.
                final InputStream from_client = client.getInputStream();
                final OutputStream to_client = client.getOutputStream();
                // Подключаемся к реальному серверу.
                // Если подключиться не удается, посылаем клиенту сообщение об ошибке,
                // отключаемся от него и продолжаем ожидать новых подключений.
                try { server = new Socket(host, remoteport); }
                catch (IOException e) {
                    PrintWriter out = new PrintWriter(to_client);
                    out.print("Проксисервер не смог соединиться с " + host + ":"+
                            remoteport + ":\n" + e + "\n");
                    out.flush();
                    client.close();
                    continue;
                }
                // Получаем потоки для общения с сервером.
                final InputStream from_server = server.getInputStream();
                final OutputStream to_server = server.getOutputStream();
                // Создаем поток исполнения для чтения клиентских запросов и передачи их
                // серверу. Нам приходится использовать отдельный процесс, так как
                // запросы и ответы могут поступать асинхронно.
                Thread t = new Thread() {
                    public void run() {
                        int bytes_read;
                        try {
                            while((bytes_read=from_client.read(request))!=-1) {
                                to_server.write(request, 0, bytes_read);
                                to_server.flush();
                            }
                        }
                        catch (IOException e) {}
                        // Клиент закрыл подключение к нам, так что закрываем
                        // наше подключение к серверу. Это повлечет за собой также
                        // выход из цикла от сервера к клиенту в главном потоке исполнения.
                        try {to_server.close();} catch (IOException e) {}
                    }
                };
                // Запускаем поток исполнения запросов от клиента к серверу
                t.start();
                // В то же время в главном потоке исполнения считываем ответы сервера
                // и передаем их клиенту. Это будет делаться параллельно с только что
                // созданным потоком исполнения запросов от клиента к серверу.
                int bytes_read;
                try {
                    while((bytes_read = from_server.read(reply)) != -1) {
                        to_client.write(reply, 0, bytes_read);
                        to_client.flush();
                    }
                }
                catch(IOException e) {}
                // Сервер закрыл подключение к нам, так что и мы закрываем свое
                // подключение к нашему клиенту. Это повлечет
                // за собой завершение исполнения другого потока.
                to_client.close();
            }
            catch (IOException e) { System.err.println(e); }
            finally { // Как бы то ни было, закрываем соединения.
                try {
                    if (server != null) server.close();
                    if (client != null) client.close();
                }
                catch(IOException e) {}
            }
        }
    }
}





