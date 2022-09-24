/**
 * ChatClient.java
 * チャットクライアントプログラム
 * ユーザーからの標準入力を受け付け、チャットサーバーに送信する。
 * また、別スレッドにてチャットサーバーからのメッセージを受信する。
 */

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class ChatClient extends Thread {
  private static SocketChannel channel;
  private static Selector selector;
  private static BufferedReader reader;
  private static CharsetEncoder encoder;
  private static CharsetDecoder decoder;
  private static boolean sendNameFlag;

  public static void main(String[] args) {
    ChatClient thread = null;

    try {
      System.out.println("┌──────────────────────────────────┐\n" +
      "│         Start ChatClient         │\n" +
      "└──────────────────────────────────┘");

      // 引数からソケットサーバー情報を取得
      int port = Integer.parseInt(args[1]);
      String host = args[0];

      // 名前を入力する
      sendNameFlag = false;
      reader = new BufferedReader(new InputStreamReader(System.in));
      System.out.println("名前を入力してください: ");
      String name = "";
      do {
        name = reader.readLine();
      } while (name.equals(""));

      // ソケットサーバーに接続
      channel = SocketChannel.open();
      channel.connect(new InetSocketAddress(host, port));
      channel.configureBlocking(false);
      selector = Selector.open();
      channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

      // 文字列変換器の構築
      Charset charset = Charset.forName("UTF-8");
      encoder = charset.newEncoder();
      decoder = charset.newDecoder();

      // 受信スレッドの開始
      thread = new ChatClient();
      thread.start();

      // selector.selectNow()は利用可能なチャンネル数を返す。
      while (thread.isAlive()) {
        while (selector.selectNow() > 0) {
          Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
          while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if (key.isWritable()) {
              // 送るメッセージを決定する
              String message;
              if (!sendNameFlag) {
                message = name;
                sendNameFlag = true;
              } else {
                message = getMessage();
              }

              // メッセージを送る
              if (!thread.isAlive()) {
                throw new SocketException();
              }
              send((SocketChannel) key.channel(), message);
            }
          }
        }
        TimeUnit.MILLISECONDS.sleep(1);
      }
    } catch (SocketException e) {
      System.out.println("サーバーと接続できなかったためプログラムを終了します。");
    } catch (IOException | InterruptedException e) {
      // 受信スレッドが動いていた場合停止する。
      if (thread != null) {
        thread.interrupt();
      }

      e.printStackTrace();
    }
  }

  /**
   * 別スレッドでソケットサーバーからのメッセージを受信・表示する。
   */
  public void run() {
    try {
      while (true) {
        while (selector.selectNow() > 0) {
          Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
          while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if (key.isReadable()) {
              onMessage(channel);
            }
          }
        }
        TimeUnit.MILLISECONDS.sleep(1);
      }
    } catch (SocketException e) {
      System.out.println("サーバーとの接続が切れました。");
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * ソケットサーバーからメッセージを受信したときの処理。
   * メッセージを表示する。
   * 
   * @param sc ソケットサーバー
   * @throws IOException
   */
  private static void onMessage(SocketChannel sc) throws IOException {
    sc.configureBlocking(false);

    // バイナリメッセージを文字列に変換
    ByteBuffer bb = ByteBuffer.allocate(1024);
    bb.clear();
    sc.read(bb);
    bb.flip();
    String result = decoder.decode(bb).toString();

    // メッセージを表示
    if (result.length() > 0) {
      System.out.println("受信(" + sc.getRemoteAddress() + "): " + result);
    }
  }

  /**
   * メッセージをソケットサーバーに送信する。
   * 
   * @param sc      送り先のソケットサーバー
   * @param message 送信するメッセージ
   * @throws IOException
   */
  private static void send(SocketChannel sc, String message) throws IOException {
    sc.configureBlocking(false);
    sc.write(encoder.encode(CharBuffer.wrap(message)));
  }

  /**
   * 標準入力からメッセージを受け取る。
   * 
   * @return 受け取ったメッセージ
   * @throws IOException
   */
  private static String getMessage() throws IOException {
    System.out.println("文字列を入力してください: ");
    String message = reader.readLine();
    return message;
  }
}
