# SimpleChatApplication

インターンシップで作成した物を作り直しました。
以下は元のREADME.mdを一部改変したものです。


## ■環境構築

### ○Git for Windows

- 以下のURLから環境に合わせてダウンロードし、インストーラを実行する。
    - https://gitforwindows.org/

### ○OpenJDK

- 以下のURLのBuildsから環境に合わせて'openjdk-16.0.2'をダウンロードする。
    - http://jdk.java.net/16/
- Windows10環境であれば、以下の場所に設置する。
    - `C:\Program Files\jdk\jdk-16.0.2`
- 環境変数のPATHを編集する。
    - コントロールパネル->システムとセキュリティ->システム->システムの詳細設定->環境変数 を開く。
    - システム環境変数の`PATH`に以下を追加する。
        - `C:\Program Files\jdk\jdk-16.0.2\bin`

### ○Visual Studio Code

- 以下のURLから環境に合わせてダウンロードし、インストーラを実行する。
    - https://code.visualstudio.com/download

- VSCodeを起動し、ウィンドウ左の拡張機能から、`Java Extension Pack`を  
  検索しインストールする。

### ○ローカルリポジトリを作成

- PowerShellを開き、任意の場所で以下のコマンドを実行する。
    - `git clone https://github.com/TbrtTkyk/SimpleChatApplication.git`

## ■開発に関して

### ○デバッグ

- VSCodeでjavaファイルを開き、右上の再生ボタンを押すことでデバッグ実行が可能。
- `ChatServer.java`でデバッグ実行すると、クライアントからのメッセージ入力待ちとなる。
- この状態で`ChatClient.java`でデバッグ実行すると、メッセージが送信される。
- それぞれのコンソールに受信・送信したメッセージが表示される。
- デバッグモードで使用されるvscodeターミナルでは日本語の標準入力が文字化けする。

### ○コンパイル

- ターミナルから以下のコマンドを実行する。
    - `javac -encoding UTF8 ChatServer.java`
    - `javac -encoding UTF8 ChatClient.java`
- 以下のコマンドで実行する。
    - `java "-Dfile.encoding=CP932" ChatServer 9000`
    - `java "-Dfile.encoding=CP932" ChatClient 127.0.0.1 9000`

### ○参考リンク

- SocketChannelについて
    - ノンブロッキングで通信処理を行うことが可能。
    - https://nompor.com/2018/06/01/post-3651/

- Selectorについて
    - シングルスレッドで複数チャネルの処理が可能。
    - https://tutuz-tech.hatenablog.com/entry/2019/05/18/162416 サンプルコードが役立ちそう
    - イテレータオブジェクトの扱いについて知っておくべきかもしれない
    - https://qiita.com/yoshi389111/items/c24f8beefb7b96cad921