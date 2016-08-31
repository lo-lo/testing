import java.io.*;
import java.net.*;
import java.util.*;
/**
 * Этот класс представляет собой универсальный шаблон настраиваемого
 * многопоточного сервера. Он ожидает подключений по любому числу
 * заданных портов и, получив подключение к порту, передает потоки ввода
 * и вывода заданному объекту Service, осуществляющему реальное обслуживание.
 * Он может ограничивать число одновременных подключений и регистрировать
 * свои действия в заданном потоке.
 **/
public class Server {
    /**
     * Метод main() для запуска сервера как самостоятельной программы.
     * Аргументы программы, задаваемые в командной строке, должны образовывать
     * пары, состоящие из названия службы и номера порта. Для каждой пары
     * программа динамически загружает названный класс Service, создает его
     * экземпляр и приказывает серверу предоставить эту службу по заданному
     * порту. Специальный аргумент –control, за которым должны следовать
     * пароль и порт, запускает специальную управляющую службу сервера,
     * работающую на заданном порте, защищенном заданным паролем.
     **/
    public static void main(String[] args) {
        try {
            if (args.length < 2) // Проверяем число аргументов
                throw new IllegalArgumentException("Должен быть указан сервис");
            // Создаем объект Server, использующий стандартный вывод в качестве
            // регистрационного журнала и ограничивающий число
            // одновременных подключений десятью.
            Server s = new Server(System.out, 10);
            // Анализ списка аргументов
            int i = 0;
            while(i < args.length) {
                if (args[i].equals("control")) { // Обработка аргумента control
                    i++;
                    String password = args[i++];
                    int port = Integer.parseInt(args[i++]);
                    // добавляем управляющую службу
                    s.addService(new Control(s, password), port);
                }
                else {
                    // В противном случае запускаем названную службу по заданному порту.
                    // Динамически загружаем класс Service и создаем его экземпляр.
                    String serviceName = args[i++];
                    Class serviceClass = Class.forName(serviceName);
                    Service service = (Service)serviceClass.newInstance();
                    int port = Integer.parseInt(args[i++]);
                    s.addService(service, port);
                }
            }
        }
        catch (Exception e) { // Отображаем сообщение, если чтото не в порядке.
            System.err.println("Сервер: " + e);
            System.err.println("Формат: java Server " +
                    "[control <password> <port>] " +
                    "[<servicename> <port> ... ]");
            System.exit(1);
        }
    }
    // Параметры состояния сервера
    Map services;   // Хештаблица, связывающая порты с объектами Listener
    Set connections;  // Набор текущих подключений
    int maxConnections;  // Лимит одновременных подключений
    ThreadGroup threadGroup; // Группа всех наших потоков исполнения
    PrintWriter logStream;  // Сюда мы направляем наш регистрационный вывод
    /**
     * Это конструктор сервера Server(). Ему должны передаваться поток (stream),
     * в который направляется регистрационный вывод (возможно, null),
     * и максимальное число одновременных подключений.
     **/
    public Server(OutputStream logStream, int maxConnections) {
        setLogStream(logStream);
        log("Сервер запущен");
        threadGroup = new ThreadGroup(Server.class.getName());
        this.maxConnections = maxConnections;
        services = new HashMap();
        connections = new HashSet(maxConnections);
    }
    /**
     * Открытый (public) метод, устанавливающий текущий регистрационный поток.
     * Аргументу null соответствует отключение регистрации.
     **/
    public synchronized void setLogStream(OutputStream out) {
        if (out != null) logStream = new PrintWriter(out);
        else logStream = null;
    }
    /** Записываем заданную строку в регистрационный журнал */
    protected synchronized void log(String s) {
        if (logStream != null) {
            logStream.println("[" + new Date() + "] " + s);
            logStream.flush();
        }
    }
    /** Записываем заданный объект в регистрационный журнал */
    protected void log(Object o) { log(o.toString()); }
    /**
     * Этот метод заставляет сервер открыть новую службу.
     * Он запускает заданный объект Service на заданном порте.
     **/
    public synchronized void addService(Service service, int port)
            throws IOException
    {
        Integer key = new Integer(port); // ключ хештаблицы
        // Проверяем, не занят ли этот порт какойлибо службой
        if (services.get(key) != null)
            throw new IllegalArgumentException("Порт " + port +
                    " уже используется.");
        // Создаем объект Listener, который будет ожидать подключений к этому порту
        Listener listener = new Listener(threadGroup, port, service);
        // Сохраняем его в хештаблице
        services.put(key, listener);
        // Регистрируем событие
        log("Запуск службы " + service.getClass().getName() +
                " по порту " + port);
        // Запускаем listener.
        listener.start();
    }
    /**
     * Этот метод заставляет сервер закрыть службу по заданному порту.
     * Он не закрывает существующие подключения к этой службе, а просто
     * приказывает серверу прекратить принимать новые подключения.
     **/
    public synchronized void removeService(int port) {
        Integer key = new Integer(port); // Ключ хештаблицы
        // Ищем в хештаблице объект Listener, соответствующий заданному порту
        final Listener listener = (Listener) services.get(key);
        if (listener == null) return;
        // Просим listener остановиться
        listener.pleaseStop();
        // Удаляем его из хештаблицы
        services.remove(key);
        // И регистрируем событие.
        log("Останов службы " + listener.service.getClass().getName() +
                " по порту " + port);
    }
    /**
     * Этот вложенный подкласс класса Thread «слушает сеть». Он ожидает
     * попыток подключиться к заданному порту (с помощью ServerSocket), и когда
     * получает запрос на подключение, вызывает метод сервера addConnection(),
     * чтобы принять (или отклонить) подключение. Для каждой службы Service,
     * предоставляемой сервером Server, есть один объект Listener.
     **/
    public class Listener extends Thread {
        ServerSocket listen_socket;    // Объект ServerSocket, ожидающий подключений
        int port;                      // Прослушиваемый порт
        Service service;               // Служба по этому порту
        volatile boolean stop = false; // Признак команды остановки
        /**
         * Конструктор Listener создает для себя поток исполнения в составе заданной
         * группы. Он создает объект ServerSocket, ожидающий подключений
         * по заданному порту. Он настраивает ServerSocket так, чтобы его
         * можно было прервать, за счет чего служба может быть удалена с сервера.
         **/
        public Listener(ThreadGroup group, int port, Service service)
                throws IOException
        {
            super(group, "Listener:" + port);
            listen_socket = new ServerSocket(port);
            // Задаем ненулевую паузу, чтобы accept() можно было прервать
            listen_socket.setSoTimeout(600000);
            this.port = port;
            this.service = service;
        }
        /**
         * Это вежливый способ сообщить Listener, что нужно прекратить
         * прием новых подключений
         ***/
        public void pleaseStop() {
            this.stop = true;  // Установка флага остановки
            this.interrupt();  // Прекращение блокировки в accept().
            try { listen_socket.close(); } // Прекращение ожидания новых подключений.
            catch(IOException e) {}
        }
        /**
         * Класс Listener является подклассом класса Thread, его тело приведено ниже.
         * Ожидаем запросов на подключение, принимаем их и передаем Socket
         * методу сервера addConnection.
         **/
        public void run() {
            while(!stop) { // цикл продолжается, пока нас не попросят остановиться.
                try {
                    Socket client = listen_socket.accept();
                    addConnection(client, service);
                }
                catch (InterruptedIOException e) {}
                catch (IOException e) {log(e);}
            }
        }
    }
    /**
     * Это метод, вызываемый объектами Listener, когда они принимают
     * соединение с клиентом. Он либо создает объект Connection
     * для этого подключения и добавляет его в список имеющихся подключений,
     * либо, если лимит подключений исчерпан, закрывает подключение.
     **/
    protected synchronized void addConnection(Socket s, Service service) {
        // Если лимит числа подключений исчерпан,
        if (connections.size() >= maxConnections) {
            try {
                // сообщаем клиенту, что его запрос отклонен.
                PrintWriter out = new PrintWriter(s.getOutputStream());
                out.print("В подключении отказано; " +
                        "сервер перегружен, попытайтесь подключиться позже.\n");
                out.flush();
                // И закрываем подключение клиента, которому отказано.
                s.close();
                // И, конечно, делаем об этом регистрационную запись.
                log("Подключение отклонено для " +
                        s.getInetAddress().getHostAddress() +
                        ":" + s.getPort() + ": исчерпан лимит числа подключений.");
            } catch (IOException e) {log(e);}
        }
        else { // В противном случае, если лимит не исчерпан,
            // создаем процесс Connection для обработки этого подключения.
            Connection c = new Connection(s, service);
            // Добавляем его в список текущих подключений.
            connections.add(c);
            // Регистрируем новое соединение
            log("Установлено подключение к " + s.getInetAddress().getHostAddress() +
                    ":" + s.getPort() + " по порту " + s.getLocalPort() +
                    " для службы " + service.getClass().getName());
            // И запускаем процесс Connection, предоставляющий услугу
            c.start();
        }
    }
    /**
     * Процесс Connection вызывает этот метод непосредственно перед выходом.
     * Он удаляет заданный объект Connection из набора подключений.
     **/
    protected synchronized void endConnection(Connection c) {
        connections.remove(c);
        log("Подключение к " + c.client.getInetAddress().getHostAddress() +
                ":" + c.client.getPort() + " закрыто.");
    }
    /** Этот метод изменяет максимально допустимое число подключений */
    public synchronized void setMaxConnections(int max) {
        maxConnections = max;
    }
    /**
     * Этот метод выводит в заданный поток информацию о статусе сервера.Он может
     * применяться для отладки и ниже в этом примере используется службой Control.
     **/
    public synchronized void displayStatus(PrintWriter out) {
        // Отображаем список всех предоставляемых служб
        Iterator keys = services.keySet().iterator();
        while(keys.hasNext()) {
            Integer port = (Integer) keys.next();
            Listener listener = (Listener) services.get(port);
            out.print("СЛУЖБА " + listener.service.getClass().getName()
                    + " ПО ПОРТУ " + port + "\n");
        }
        // Отображаем текущее ограничение на число подключений
        out.print("ЛИМИТ ПОДКЛЮЧЕНИЙ: " + maxConnections + "\n");
        // Отображаем список всех текущих подключений
        Iterator conns = connections.iterator();
        while(conns.hasNext()) {
            Connection c = (Connection)conns.next();
            out.print("ПОДКЛЮЧЕНИЕ К " +
                    c.client.getInetAddress().getHostAddress() +
                    ":" + c.client.getPort() + " ПО ПОРТУ " +
                    c.client.getLocalPort() + " ДЛЯ СЛУЖБЫ " +
                    c.service.getClass().getName() + "\n");
        }
    }
    /**
     * Этот подкласс класса Thread обрабатывает индивидуальные подключения
     * между клиентом и службой Service, предоставляемой настоящим сервером.
     * Поскольку каждое такое подключение обладает собственным потоком
     * исполнения, у каждой службы может иметься несколько подключений
     * одновременно. Вне зависимости от всех других используемых потоков
     * исполнения, именно это делает наш сервер многопоточным.
     **/
    public class Connection extends Thread {
        Socket client;    // Объект Socket для общения с клиентом
        Service service; // Служба, предоставляемая клиенту
/**
 * Этот конструктор просто сохраняет некоторые параметры состояния
 * и вызывает конструктор родительского класса для создания потока
 * исполнения, обрабатывающего подключение. Объекты Connection
 * создаются потоками исполнения Listener. Эти потоки являются частью группы
 * потоков сервера, поэтому процессы Connection также входят в эту группу
 **/
public Connection(Socket client, Service service) {
    super("Server.Connection:" +
            client.getInetAddress().getHostAddress() +
            ":" + client.getPort());
    this.client = client;
    this.service = service;
}
        /**
         * Это тело любого и каждого потока исполнения Connection. Все, что оно дела
         * ет, – это передает потоки ввода и вывода клиента методу serve() заданного
         * объекта Service, который несет ответственность за чтение и запись
         * в эти потоки для осуществления действительного обслуживания. Вспомним,
         * что объект Service был передан методом Server.addService() объекту
         * Listener, а затем через метод addConnection() этому объекту Connection,
         * и теперь наконец используется для предоставления услуги. Обратите внимание
         * на то, что непосредственно перед выходом этот поток исполнения всегда
         * вызывает метод endConnection(), чтобы удалить себя из набора подключений.
         **/
        public void run() {
            try {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                service.serve(in, out);
            }
            catch (IOException e) {log(e);}
            finally { endConnection(this); }
        }
    }
    /**
     * Здесь описан интерфейс Service, с которым мы так часто встречались.
     * Он определяет только один метод, который вызывается для предоставления
     * услуги. Методу serve() передаются поток ввода и поток вывода,
     * связанные с клиентом. Он может делать с ними все, что угодно,
     * только перед выходом должен закрыть их.
     *
     * Все соединения с этой службой по одному порту совместно используют один
     * объект Service. Таким образом, любое локальное состояние индивидуального
     * подключения должно храниться в локальных переменных метода serve().
     * Состояние, характеризующее все подключения по данному порту, должно
     * храниться в переменных экземпляра класса Service. Если одна служба Service
     * запущена на нескольких портах, обычно будут иметься несколько экземпляров
     * Service, по одному на каждый порт. Данные, относящиеся ко всем
     * подключениям на всех портах, должны храниться в статических переменных.
     *
     * Обратите внимание на то, что если экземпляры этого интерфейса будут
     * динамически создаваться методом main() класса Server, в их реализации
     * должен быть включен конструктор без аргументов.
     **/
    public interface Service {
        public void serve(InputStream in, OutputStream out) throws IOException;
    }
    /**
     * Очень простая служба. Она сообщает клиенту текущее время на сервере
     * и закрывает подключение.
     **/
    public static class Time implements Service {
        public void serve(InputStream i, OutputStream o) throws IOException {
            PrintWriter out = new PrintWriter(o);
            out.print(new Date() + "\n");
            out.close();
            i.close();
        }
    }
    /**
     * Это еще один пример службы. Она считывает строки, введенные клиентом,
     * и возвращает их перевернутыми. Она также выводит приветствие
     * и инструкции и разрывает подключение, когда пользователь
     * вводит строку, состоящую из точки «.».
     **/
    public static class Reverse implements Service {
        public void serve(InputStream i, OutputStream o) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(i));
            PrintWriter out =
                    new PrintWriter(new BufferedWriter(new OutputStreamWriter(o)));
            out.print("Welcome to the line reversal server.\n");
            out.print("Enter lines. End with a '.' on a line by itself.\n");
            for(;;) {
                out.print("> ");
                out.flush();
                String line = in.readLine();
                if ((line == null) || line.equals(".")) break;
                for(int j = line.length()-1; j >= 0; j--)
                out.print(line.charAt(j));
                out.print("\n");
            }
            out.close();
            in.close();
        }
    }
    /**
     * Эта служба – просто “зеркало” HTTP, точно такое же, как класс HttpMirror,
     * реализованный выше в этой главе. Она возвращает клиенту его HTTPзапросы.
     **/
    public static class HTTPMirror implements Service {
        public void serve(InputStream i, OutputStream o) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(i));
            PrintWriter out = new PrintWriter(o);
            out.print("HTTP/1.0 200 \n");
            out.print("ContentType: text/plain\n\n");
            String line;
            while((line = in.readLine()) != null) {
                if (line.length() == 0) break;
                out.print(line + "\n");
            }
            out.close();
            in.close();
        }
    }
    /**
     * Эта служба демонстрирует, как следует поддерживать состояние на протяжении
     * нескольких подключений путем сохранения его в переменных экземпляра
     * и применения синхронизованного доступа к этим переменным. Эта служба
     * подсчитывает число подключившихся к ней клиентов
     * и сообщает каждому клиенту его номер.
     **/
    public static class UniqueID implements Service {
        public int id=0;
        public synchronized int nextId() { return id++; }
        public void serve(InputStream i, OutputStream o) throws IOException {
            PrintWriter out = new PrintWriter(o);
            out.print("Ваш номер: " + nextId() + "\n");
            out.close();
            i.close();
        }
    }
    /**
     * Это нетривиальная служба. Она реализует командный протокол, дающий защищенные
     * паролем средства управления операциями сервера во время его исполнения.
     * См. метод main() класса Server, чтобы увидеть, как эта служба запускается.
     *
     * Распознаются следующие команды:
     *   password: сообщает пароль; авторизация обязательна для большинства команд
     *   add: динамически добавляет названную службу на заданном порте
     *   remove: динамически удаляет службу, работающую на заданном порте
     *   max: изменяет лимит числа подключений.
     *   status: отображает действующие службы, текущие соединения
     и лимит числа подключений
     *   help: отображает страницу помощи
     *   quit: отключение
     *
     * Эта служба выводит “подсказку” и посылает весь свой вывод в адрес клиента
     * заглавными буквами. К этой службе в каждый момент времени
     * может подключиться только один клиент.
     **/
    public static class Control implements Service {
        Server server;             // Сервер, которым мы управляем
        String password;           // Пароль, который мы требуем
        boolean connected = false; // Подключен ли уже ктото к этой службе?
        /**
         * Создаем новую службу Control. Она будет управлять заданным объектом Server
         * и будет требовать заданный пароль для авторизации. Обратите внимание
         * на то, что у этой службы нет конструктора без аргументов. Это значит,
         * что, в отличие от вышеприведенных служб, нет возможности динамически
         * создать ее экземпляр и добавить ее к списку служб сервера.
         **/
        public Control(Server server, String password) {
            this.server = server;
            this.password = password;
        }
        /**
         * Это метод serve(), осуществляющий обслуживание. Он читает строку,
         * отправленную клиентом, и применяет java.util.StringTokenizer, чтобы
         * разобрать ее на команды и аргументы. В зависимости от этой команды
         * он делает множество разных вещей.
         **/
        public void serve(InputStream i, OutputStream o) throws IOException {
            // Настраиваем потоки
            BufferedReader in = new BufferedReader(new InputStreamReader(i));
            PrintWriter out = new PrintWriter(o);
            String line; // Для чтения строк из клиентского ввода
            // Пользователь уже сказал пароль?
            boolean authorized = false;
            // Если к этой службе уже подключен клиент, отображаем
            // сообщение для этого клиента и закрываем подключение. Мы используем
            // синхронизированный блок для предотвращения “состояния гонки”.
            synchronized(this) {
                if (connected) {
                    out.print("РАЗРЕШАЕТСЯ ТОЛЬКО ОДНО ПОДКЛЮЧЕНИЕ.\n");
                    out.close();
                    return;
                }
                else connected = true;
            }
            // Это главный цикл: в нем команда считывается,
            // анализируется и обрабатывается
            for(;;) { // бесконечный цикл
                out.print("> ");         // Отображаем “подсказку”
                out.flush();             // Выводим ее немедленно
                line = in.readLine();    // Получаем пользовательский ввод
                if (line == null) break; // Выходим из цикла при получении EOF.
                try {
                    // Применяем StringTokenizer для анализа команды пользователя
                    StringTokenizer t = new StringTokenizer(line);
                    if (!t.hasMoreTokens()) continue; // если ввод пустой
                    // Выделяем из ввода первое слово и приводим его к нижнему регистру
                    String command = t.nextToken().toLowerCase();
                    // Теперь сравниваем его со всеми допустимыми командами, выполняя
                    // для каждой команды соответствующие действия
                    if (command.equals("password")) { // Команда Password
                        String p = t.nextToken();         // Получаем следующее слово
                        if (p.equals(this.password)) {    // Пароль правильный?
                            out.print("OK\n");               // Допустим, да
                            authorized = true;               // Подтверждаем авторизацию
                        }
                        else out.print("НЕВЕРНЫЙ ПАРОЛЬ\n"); // В противном случае – нет
                    }
                    else if (command.equals("add")) { // Команда Add Service
                        // Проверяем, был ли указан пароль
                        if (!authorized) out.print("НЕОБХОДИМ ПАРОЛЬ\n");
                        else {
                            // Получаем название службы и пытаемся динамически загрузить ее
                            // и создать ее экземпляр. Исключения обрабатываются ниже
                            String serviceName = t.nextToken();
                            Class serviceClass = Class.forName(serviceName);
                            Service service;
                            try {
                                service = (Service)serviceClass.newInstance();
                            }
                            catch (NoSuchMethodError e) {
                                throw new IllegalArgumentException(
                                        "У службы должен быть " +
                                                "конструктор без аргументов");
                            }
                            int port = Integer.parseInt(t.nextToken());
                            // Если никаких исключений не произошло, добавляем службу
                            server.addService(service, port);
                            out.print("СЛУЖБА ДОБАВЛЕНА\n"); // сообщаем об этом клиенту
                        }
                    }
                    else if (command.equals("remove")) { // Команда удаления службы
                        if (!authorized) out.print("НЕОБХОДИМ ПАРОЛЬ\n");
                        else {
                            int port = Integer.parseInt(t.nextToken());
                            server.removeService(port); // удаляем службу
                            out.print("СЛУЖБА УДАЛЕНА\n"); // сообщаем об этом клиенту
                        }
                    }
                    else if (command.equals("max")) { // Устанавливаем лимит числа подключений
                        if (!authorized) out.print("НЕОБХОДИМ ПАРОЛЬ\n");
                        else {
                            int max = Integer.parseInt(t.nextToken());
                            server.setMaxConnections(max);
                            out.print("ЛИМИТ ПОДКЛЮЧЕНИЙ ИЗМЕНЕН \n");
                        }
                    }
                    else if (command.equals("status")) { // Отображение состояния
                        if (!authorized) out.print("НЕОБХОДИМ ПАРОЛЬ\n");
                        else server.displayStatus(out);
                    }
                    else if (command.equals("help")) { // Команда Help
                        // Отображаем синтаксис команд. Пароль необязателен
                        out.print("КОМАНДЫ:\n" +
                                "\tpassword <password>\n" +
                                "\tadd <service> <port>\n" +
                                "\tremove <port>\n" +
                                "\tmax <maxconnections>\n" +
                                "\tstatus\n" +
                                "\thelp\n" +
                                "\tquit\n");
                    }
                    else if (command.equals("quit")) break; // Команда Quit.
                    else out.print("НЕИЗВЕСТНАЯ КОМАНДА\n"); // Ошибка
                }
                catch (Exception e) {
                    // Если в процессе анализа или выполнения команды возникло исключение,
                    // печатаем сообщение об ошибке, затем выводим
                    // подробные сведения об исключении.
                    out.print("ОШИБКА ВО ВРЕМЯ АНАЛИЗА ИЛИ ВЫПОЛНЕНИЯ КОМАНДЫ:\n" +
                            e + "\n");
                }
            }
            // Окончательно, когда происходит выход из цикла обработки команд,
            // закрываем потоки (streams) и присваиваем флагу connected значение false,
            // так что теперь могут подключаться новые клиенты.
            connected = false;
            out.close();
            in.close();
        }
    }
}











