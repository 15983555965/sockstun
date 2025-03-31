package hev.sockstun.v2ray;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;

public class IOUtils {
    private static final String TAG = "IOUtils";
    private static final int BUFFER_SIZE = 32 * 1024;

    private IOUtils() {
        // 私有构造函数防止实例化
    }

    /**
     * inputStream copy to out
     *
     * @param inputStream input stream
     * @param out output stream
     */
    public static void copy(InputStream inputStream, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int temp;
        while (true) {
            temp = inputStream.read(buffer);
            if (temp == -1) {
                break;
            }
            out.write(buffer, 0, temp);
        }
        out.flush();
    }

    /**
     * read string by input stream
     *
     * @param inputStream input stream
     * @return read failed return ""
     */
    public static String readString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[BUFFER_SIZE];
        int temp;
        while (true) {
            temp = inputStream.read(buffer);
            if (temp == -1) {
                break;
            }
            builder.append(new String(buffer, 0, temp));
        }
        return builder.toString();
    }

    /**
     * write string
     *
     * @param file    file path
     * @param content string content
     * @return write failed return false.
     */
    public static boolean writeString(String file, String content) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(content);
            return true;
        } catch (IOException e) {
//            VayLog.e(TAG, "writeString", e);
            return false;
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            // ignored
        }
    }

    public static void close(OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            // Ignored
        }
    }

    public static void close(Writer writer) {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            // Ignored
        }
    }

    public static void close(Reader reader) {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            // Ignored
        }
    }

    public static void disconnect(HttpURLConnection conn) {
        if (conn != null) {
            conn.disconnect();
        }
    }
}