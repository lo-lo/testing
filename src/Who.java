import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
/**
 * Этот апплет соединяется с finger сервером на узле, с которого он получен,
 * чтобы узнать, кто в настоящий момент зарегистрирован в системе.
 * Так как это ненадежный апплет, он может соединяться только с тем узлом,
 * с которого он был загружен. Поскольку вебсерверы сами редко запускают
 * finger серверы, этот апплет будет часто использоваться вместе
 * с проксисервером, чтобы обслуживаться с узла, на котором
 * fingerсервер есть.
 **/
public class Who extends Applet implements ActionListener, Runnable {
    Button who; // Кнопка в апплете
    /**
     * Метод init() просто создает кнопку, которая будет использоваться в апплете.
     * Когда пользователь щелкнет на кнопке, мы увидим, кто зарегистрирован.
     **/
    public void init() {
        who = new Button("Who?");
        who.setFont(new Font("SansSerif", Font.PLAIN, 14));
        who.addActionListener(this);
        this.add(who);
    }
    /**
     * После нажатия кнопки запускаем процесс, который подключится
     * к fingerсерверу и покажет список зарегистрированных пользователей
     **/
    public void actionPerformed(ActionEvent e) { new Thread(this).start(); }
    /**
     * Это метод, осуществляющий сетевые операции и отображающий результаты.
     * Он реализован как отдельный поток исполнения, поскольку на его завершение
     * может понадобиться некоторое время, а методы апплета должны
     * выполняться быстро.
     **/
    public void run() {
        // Отключаем кнопку, так что за один раз нельзя будет отправить
        // несколько запросов...
        who.setEnabled(false);
        // Создаем окно для отображения в нем вывода
        Frame f = new Frame("Список зарегистрированных пользователей: Подключение...");
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                ((Frame)e.getSource()).dispose();
            }
        });
        TextArea t = new TextArea(10, 80);
        t.setFont(new Font("MonoSpaced", Font.PLAIN, 10));
        f.add(t, "Center");
        f.pack();
        f.show();
        // Смотрим, кто зарегистрирован
        Socket s = null;
        PrintWriter out = null;
        BufferedReader in = null;
        try {
            // Соединяемся с портом 79 (стандартный fingerпорт) на узле,
            // с которого апплет был загружен.
            String hostname = this.getCodeBase().getHost();
            s = new Socket(hostname, 79);
            // Создаем потоки
            out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            // Отправляем на fingerсервер пустую строку, говоря тем самым,
            // что нам интересен список зарегистрированных пользователей,
            // а не информация об отдельном пользователе.
            out.print("\n");
            out.flush(); // Отправляем немедленно!
            // Теперь читаем ответ сервера и отображаем его в текстовой области.
            // Сервер должен посылать строки, оканчивающиеся символом \n.
            // Метод readLine() должен распознавать конец строки, даже если
            // он работает на Mac, где строки завершаются символом \r
            String line;
            while((line = in.readLine()) != null) {
                t.append(line);
                t.append("\n");
            }
            // Обновляем заголовок окна, показывая, что мы закончили
            f.setTitle("Кто зарегистрирован на: " + hostname);
        }
        // Если чтото оказывается не в порядке, просто отображаем сообщение об ошибке
        catch (IOException e) {
            t.append(e.toString());
            f.setTitle("Список зарегистрированных пользователей: Ошибка");
        }
        // И в конце не забываем закрыть потоки!
        finally {
            try { in.close(); out.close(); s.close(); } catch(Exception e) {}
        }
        // И снова делаем кнопку активной
        who.setEnabled(true);
    }
}

