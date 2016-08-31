
import java.io.*;
import java.net.*;
/**
 * Эта программа отправляет email при помощи mailto: URL
 * обработчик протокола mailto:
 **/
public class SendMail {
    public static void main(String[] args) {
        try {
            // Если пользователь задал почтовый узел, сообщаем об этом системе.
            // Если почтовый  узел  был  вами  указан,  он  сохраняется  в  системном  свойстве mail.host,
            // которое считывается встроенным обработчиком протокола mailto:
            if (args.length >= 1)
                System.getProperties().put("mail.host", args[0]);
            // Поток Reader для чтения с консоли
            BufferedReader in =
                    new BufferedReader(new InputStreamReader(System.in));
            // Запрашиваем у пользователя строки from, to и subject
            System.out.print("От кого: ");
            String from = in.readLine();
            System.out.print("Кому: ");
            String to = in.readLine();
            System.out.print("Тема: ");
            String subject = in.readLine();
            // Устанавливаем сетевое подключение для отправки почты
            URL u = new URL("mailto:" + to);      // Создаем mailto: URL
            URLConnection c = u.openConnection(); // Создаем для него URLConnection
            //
            c.setDoInput(false);         // Сообщаем, что он не будет
            // использоваться для ввода
            //
            c.setDoOutput(true);         // Сообщаем, что для вывода
            // он использоваться будет
            System.out.println("Подключение...");  // Сообщение пользователю
            System.out.flush();          // Отображается немедленно
            c.connect();                 // Соединяемся с узлом связи
            PrintWriter out =            // Получаем поток вывода в направлении узла
                    new PrintWriter(new OutputStreamWriter(c.getOutputStream()));
            // Выводим заголовки сообщения. Не позволяем пользователю сообщать
            // ложный обратный адрес
            out.print("От кого: \"" + from + "\" <" +
                    System.getProperty("user.name") + "@" +
                    InetAddress.getLocalHost().getHostName() + ">\n");
            out.print("Кому: " + to + "\n");
            out.print("Тема: " + subject + "\n");
            out.print("\n"); // Пустой строкой завершается список заголовков
            // Теперь просим пользователя ввести тело сообщения
            System.out.println("Введите сообщение. " +
                    "Завершите его строкой, содержащей одну точку.");
            // Построчно считываем сообщение и отправляем его в поток вывода
            String line;
            for(;;) {
                line = in.readLine();
                if ((line == null) || line.equals(".")) break;
                out.print(line + "\n");
            }
            // Закрываем (и очищаем) поток по завершении сообщения
            out.close();
            // Сообщаем пользователю, что сообщение успешно отправлено
            System.out.println("Сообщение отправлено.");
        }
        catch (Exception e) { // Обрабатываем всевозможные исключения
            // и сообщаем об ошибках
            System.err.println(e);
            System.err.println("Формат: java SendMail [<mailhost>]");
        }
    }
}

