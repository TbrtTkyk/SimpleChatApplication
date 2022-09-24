/**
 * ChatServer.java
 * チャットサーバープログラム
 * チャットクライアントからの接続を複数受け付け、メッセージの中継ぎをする。
 * その際、メッセージの内容をフィルタを通して変換する。
 */

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.*;

public class ChatServer {
  private static ServerSocketChannel channel;
  private static Selector selector;
  private static CharsetEncoder encoder;
  private static CharsetDecoder decoder;
  private static List<SocketChannel> clients = new ArrayList<>();
  private static Map<String, String> nameList = new HashMap<>();

  // 特殊コマンド定義
  // 「@help」でコマンド一覧を自分に送信する
  private static Pattern helpPattern = Pattern.compile("@help[ |　]*");
  private static String helpMessage = "コマンドリスト\n" +
    "\t@help: コマンド一覧を自分に送る。\n" +
    "\t@member: 参加者名一覧を自分に送る。\n" +
    "\t@wisper 対象 内容: 対象にのみ内容を伝える。\n";
  // 「@wisper 対象 メッセージ」で対象にのみメッセージを送る
  private static Pattern wisperPattern = Pattern.compile("@wisper[ |　](.+?)[ |　](.*)");
  // 「@member」でサーバに接続している名前一覧を送り返す
  private static Pattern memberPattern = Pattern.compile("@member[ |　]*");

  public static void main(String[] args) {
    try {
      System.out.println("┌──────────────────────────────────┐\n" +
          "│         Start ChatServer         │\n" +
          "└──────────────────────────────────┘");
      
      // サーバーソケット作成
      int port = Integer.parseInt(args[0]);
      channel = ServerSocketChannel.open();
      channel.socket().bind(new InetSocketAddress(port));
      channel.configureBlocking(false);

      System.out.println("host: 0.0.0.0 port: " + port);
      
      // Selectorの準備
      selector = Selector.open();
      channel.register(selector, SelectionKey.OP_ACCEPT);

      // 文字列変換器の構築
      Charset charset = Charset.forName("UTF-8");
      encoder = charset.newEncoder();
      decoder = charset.newDecoder();

      while (true) {
        // selector.selectNow()は利用可能なチャンネル数を返す。
        while (selector.selectNow() > 0) {
          Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
          while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();
            if (key.isAcceptable()) {
              onConnected();
            } else if (key.isReadable()) {
              onMessage((SocketChannel) key.channel());
            }
          }
        }
        TimeUnit.MILLISECONDS.sleep(1);
      }
    } catch(IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * クライアントから接続要求があった時の処理。
   * クライアント一覧に保存する。
   * 
   * @throws IOException
   */
  private static void onConnected() throws IOException {
    SocketChannel sc = channel.accept();
    if (sc == null)
      return;

    sc.configureBlocking(false);
    sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    clients.add(sc);
    System.out.println("接続開始(" + sc.getRemoteAddress() + ")");
  }

  /**
   * クライアントからメッセージを受信した時の処理。
   * メッセージを表示し、その他のクライアントにブロードキャストする。
   * 
   * @param sc メッセージを送信したクライアント
   * @throws IOException
   */
  private static void onMessage(SocketChannel sc) throws IOException {
    sc.configureBlocking(false);

    try {
      // バイナリメッセージを文字列に変換
      ByteBuffer bb = ByteBuffer.allocate(1024);
      bb.clear();
      sc.read(bb);
      bb.flip();
      String result = decoder.decode(bb).toString();

      // メッセージに対して処理を行う
      if (result.length() > 0) {
        String clientAddr = sc.getRemoteAddress().toString();
        if (!nameList.containsKey(clientAddr)) {
          // 不明のクライアントの最初のメッセージを名前として登録する
          System.out.println("名前登録: " + clientAddr +  " <--> " + result);
          nameList.put(clientAddr, result);
          serverMessage(result + "さんが入室しました。");
        } else {
          // メッセージを受信した
          System.out.println("受信(" + clientAddr + "): [" + getName(clientAddr) + "]" + result);
          Matcher helpMatcher = helpPattern.matcher(result);
          Matcher wisperMatcher = wisperPattern.matcher(result);
          Matcher memberMatcher = memberPattern.matcher(result);
          if (helpMatcher.find()) {
            // コマンドリストを取得する
            sc.write(encoder.encode(CharBuffer.wrap(helpMessage)));
          } else if (wisperMatcher.find()) {
            // 特定のクライアントに囁く
            String target = wisperMatcher.group(1);
            String message = wisperMatcher.group(2);
            wisper(sc, target, message);
          } else if (memberMatcher.find()) {
            // サーバーに接続しているクライアントの名前を送り返す
            sc.write(encoder.encode(CharBuffer.wrap("<参加者>" + getMember("、", "なし"))));
          } else {
            // 全体に伝える（通常メッセージ）
            sc.write(encoder.encode(CharBuffer.wrap("<発言>" + result)));
            textMessage(sc, result);
          }
        }
      }
    } catch (SocketException e) {
      // クライアントとの接続が切断された場合、SocketException: Connection resetが発生する。
      String clientAddr = sc.getRemoteAddress().toString();
      System.out.println("接続終了(" + clientAddr + ")");
      sc.close();
      clients.remove(sc);

      // 除名処理
      serverMessage(getName(clientAddr) + "さんが退室しました。");
      nameList.remove(clientAddr);
    }
  }

  /**
   * メッセージを加工して、他の参加者に送信する。
   * 
   * @param sc      除外するクライアント(元メッセージを送信したクライアント)
   * @param message 送信するメッセージ
   * @throws IOException
   */
  private static void textMessage(SocketChannel sc, String message) throws IOException {
    Predicate<SocketChannel> func = (SocketChannel client) -> {
      try {
        return !isSameClient(sc, client);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
    String filteredMessage = message;
    sendToFilteredClient("[" + getName(sc) + "]" + filteredMessage, func);
  }

  /**
   * メッセージを加工して、特定の名前のクライアントにのみ送信する。
   * 
   * @param sc      送信したクライアント
   * @param target  送信対象者の名前
   * @param message 送信するメッセージ
   * @throws IOException
   */
  private static void wisper(SocketChannel sc, String target, String message) throws IOException {
    Predicate<SocketChannel> func = (SocketChannel client) -> {
      try {
        return isSameName(client, target);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
    String filteredMessage = message;
    sendToFilteredClient("<wisper>[" + getName(sc) + "]" + filteredMessage, func);
  }

  /**
   * サーバーメッセージとして、参加者全員にメッセージを送る
   * 
   * @param message 送信するメッセージ
   * @throws IOException
   */
  private static void serverMessage(String message) throws IOException {
    sendToFilteredClient(message, (SocketChannel client) -> true);
  }

  /**
   * 条件を満たすクライアントへメッセージを送信する
   * 
   * @param message 送信するメッセージ
   * @param func    クライアントの選別条件式
   * @throws IOException
   */
  private static void sendToFilteredClient(String message, Predicate<SocketChannel> func) throws IOException {
    for (SocketChannel client : clients) {
      // 切断されていればリストから削除する。
      if (!client.isConnected()) {
        client.close();
        clients.remove(client);
        continue;
      }

      if (func.test(client)) {
        client.write(encoder.encode(CharBuffer.wrap(message)));
      }
    }
  }


  /**
   * アドレス(SocketChannel)から名前を取得する
   * 
   * @param sc クライアント
   * @return クライアントに割り当てられている名前
   * @throws IOException
   */
  private static String getName(SocketChannel sc) throws IOException {
    return getName(sc.getRemoteAddress().toString());
  }

  /**
   * アドレス(String)から名前を取得する
   * 
   * @param socketAddr クライアント
   * @return クライアントに割り当てられている名前
   */
  private static String getName(String socketAddr) {
    return nameList.getOrDefault(socketAddr, "不明さん");
  }

  /**
   * サーバーに接続しているクライアントの名前一覧を取得する
   * 
   * @param separator
   * @param emptyCase
   * @return
   */
  private static String getMember(String separator, String emptyCase) {
    if (nameList.isEmpty())
      return emptyCase;
    String text = "";
    Iterator<String> iter = nameList.values().iterator();
    while (iter.hasNext()) {
      text += iter.next();
      if (iter.hasNext())
        text += separator;
    }
    return text;
  }

  /**
   * クライアントが同一であるか判定する。
   * 
   * @param a クライアントA
   * @param b クライアントB
   * @return 同一クライアントであればtrue、異なるクライアントであればfalse。
   * @throws IOException
   */
  private static boolean isSameClient(SocketChannel a, SocketChannel b) throws IOException {
    String addrA = a.getRemoteAddress().toString();
    String addrB = b.getRemoteAddress().toString();

    return addrA.equals(addrB);
  }

  /**
   * クライアントが指定の名前であるか判定する。
   * 名前が登録されていない場合、必ずfalseを返す。
   * 
   * @param sc   クライアント
   * @param name 名前
   * @return 指定の名前で登録されていればtrue、そうでなければfalse。
   * @throws IOException
   */
  private static boolean isSameName(SocketChannel sc, String name) throws IOException {
    String scAddr = sc.getRemoteAddress().toString();

    return nameList.containsKey(scAddr) ? getName(sc).equals(name) : false;
  }
}
