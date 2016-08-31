import java.io.*;
import java.net.*;
import java.util.Date;

/**
 * применение URLConnection для получения информации о типе, размере, дате и других свойствах содержимого ресурса, на который
 * указывает URL
 */

public class GetURLInfo {
    public static void printinfo(URL url) throws IOException {
        //Получаем объект URLConnection из URL
        URLConnection c = url.openConnection();

        //Создаем подключение к URL
        c.connect();

        //Отображаем информацию о содержимом URL
        System.out.println(" Тип содержимого: " + c.getContentType());
        System.out.println(" Кодировка содержимого: " + c.getContentEncoding());
        System.out.println(" Размер содержимого: " + c.getContentLength());
        System.out.println(" Дата: " + new Date(c.getDate()));
        System.out.println(" Последняя модификация: " + new Date(c.getLastModified()));
        System.out.println(" Срок годности: " + new Date(c.getExpiration()));

        //Если это HTTP-подключение, отображаемм некоторую добавочную информацию
        if(c instanceof HttpURLConnection){
            HttpURLConnection h = (HttpURLConnection)c;
            System.out.println(" Метод запроса: " + h.getRequestMethod());
            System.out.println(" Сообщение ответа: " + h.getResponseMessage());
            System.out.println(" Код ответа: " + h.getResponseCode());
        }
    }
    //Создаем объект URL, для отображения информации о нем вызываем printinfo()
    public static void main(String[] args) {
        try{
            printinfo(new URL(args[0]));
        }catch (Exception e){
            System.err.println(e);
            System.err.println("Формат: java GetURLInfo <URL> ");
        }
    }
}

