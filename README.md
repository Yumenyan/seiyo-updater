# 星陽アップデーター 1.6

Minecraft Java Edition 1.19.2／Fabric Loader 0.14.25 以降向けです。Java 17 以降が必要です。
プレイヤーの更新にはクライアント用 Mod を使います。VPS 等の専用サーバーは、付属の seiyo-tools.jar で起動前に更新できます。

## 参加者の初回設定

1. Minecraft を閉じます。星陽インスタンスの **mods/** に、**release/seiyo-updater-1.6.0+mc1.19.2.jar** を入れます。旧版のアップデーター JAR がある場合は入れ替えてください。
2. 配布元から受け取った **seiyo-updater.json** を、そのインスタンスの **config/** に入れます。タイトル画面の **星陽アップデート → 設定フォルダー** からも場所を開けます。
新規導入ではランチャーの JVM 引数を追加せずに使えます。

### 旧版で SYP 化した Mod がある場合

旧版のブートストラップを有効にした状態で 1.5 を起動し、更新画面で **JAR へ移行** と表示された項目を適用してください。mods/ の管理対象が通常の JAR に置き換わります。カタログから外れた旧 Mod の .syp が残っている場合は、それらも移行または退避してから、ブートストラップ用の JVM 引数を外してください。

旧版の設定を復元する必要がある場合は **release/seiyo-bootstrap.jar** をインスタンスの直下に置き、ランチャーの JVM 引数に以下のように追加します。

```text
"-javaagent:D:/Minecraft/Seiyo/seiyo-bootstrap.jar=D:/Minecraft/Seiyo"
```

macOS の例です。

```text
"-javaagent:/Users/example/Minecraft/Seiyo/seiyo-bootstrap.jar=/Users/example/Minecraft/Seiyo"
```

旧版のブートストラップは Fabric が Mod を探す前に動くため、**mods/** の外に置きます。

## 更新の流れ

1. タイトル画面から **星陽アップデート** を開きます。未導入・変換・更新の対象を確認します。必須項目と選択した項目の依存先は自動で選ばれます。
2. **取得してゲームを終了** を押します。JAR／ZIP を取得し、サイズ・SHA-256 または SHA-512・アーカイブの情報を確認します。Mod は元の **.jar** のまま保存し、リソースパックだけ **.syp** に変換します。
3. ゲーム終了後にファイルを差し替えます。少し待ってランチャーから再起動してください。リソースパックはいつもの一覧に表示されます。初めて入れたものは一覧から有効にします。

管理対象の Mod は **mods/** に JAR として、パックは **resourcepacks/<保存名>.zip.syp** に保存します。Modrinth のメタデータだけで登録した Mod は、更新前後で同じファイルを照合できるよう、カタログ ID を JAR の保存名に使います。同じパックの更新では ZIP の保存名を固定すると、Minecraft の有効化状態を引き継げます。カタログから外した項目の既存ファイルは残ります。

## 管理者の配布手順

1. GitHub リポジトリに付属の **.github/workflows/pages.yml** と **site/** を置きます。リポジトリの **Settings → Pages → Source** で **GitHub Actions** を選びます。
2. **tools/seiyo-catalog-generator.html** をローカルで開きます。**Modrinth から探す** で Mod 名を検索し、プロジェクトと Minecraft 1.19.2／Fabric に対応するバージョンを選んで追加できます。配布ページ・個別バージョンページや JAR／ZIP の直接 HTTPS URL を貼る方法も使えます。Modrinth の配布ページの URL を貼った場合は、対応する最新正式版を選びます。どちらの方法でも取得した版の直接 URL をカタログに記録します。配布元がブラウザーからの取得を許可していない場合は、同じ JAR／ZIP を手元から選べます。Modrinth からの登録では API のサイズと SHA-512 を使い、インストール時にファイル本体を取得して検証します。その他の直接 URL でダウンロードを省略するには、詳細欄にサイズ・SHA-256 と登録情報を入力してください。ローカルの JAR を選んだ場合は Mod ID とバージョンを読み取ります。
3. **manifest.json を出力**し、GitHub リポジトリの **site/manifest.json** に置いて既定ブランチへ反映します。Actions が **site/** を公開します。
4. 初回だけ **参加者用設定を作る（初回）** を開き、公開した **manifest.json** の HTTPS URL を入力して **seiyo-updater.json** を出力します。参加者用設定に必要なのは配布 URL だけです。
5. 次回は公開中の **manifest.json** を開いて項目を編集します。配布番号は自動で1つ進みます。同じ URL でファイルを差し替えた場合は **URL から再取得して保存** を使うと、サイズとハッシュを取り直せます。旧版の署名付きカタログも一覧として開けます。旧形式の暗号化項目は、通常の JAR／ZIP を取得できる URL を指定し直してから出力してください。

Google ドライブのファイルを登録するときは、ZIP を展開して **tools/drive-editor.cmd** を実行し、開いたジェネレーターに共有 URL を貼ってください。ブラウザーが直接読めない場合に備え、ローカルの取得ツールがファイルを読み取ります。Google アカウントにログインしていない参加者もダウンロードできる共有設定が必要です。共有権限・ダウンロード制限・利用上限は Google ドライブ側の設定に従います。クライアントも 1.6 に更新すると Google ドライブの確認画面を処理できます。

Modrinth のメタデータだけで登録した項目を含むカタログには、アップデーター 1.4 以降が必要です。1.4 は既存の SHA-256 形式も読み込めます。

署名なしへ移行する場合は、新カタログを公開する前に参加者へアップデーター 1.3 を配ってください。1.3 は旧設定で旧カタログを読み込めます。新カタログの公開時に参加者用の **seiyo-updater.json** を入れ替えます。

共同開発用に公開済みの元ファイルは、直接 URL から取得できます。**.syp** とカタログの難読化は可逆です。署名なしの配布では、指定した HTTPS 配布先から取得したカタログを信頼します。その配布先を書き換えられる人は、配布ファイルと照合用のハッシュも変更できます。

## VPS の Fabric 専用サーバーを更新する

1. サーバーを停止し、サーバーフォルダー直下に **release/seiyo-tools.jar** をコピーします。
2. **config/seiyo-server-updater.json** を作成し、配布カタログの URL とサーバーに入れる Mod の **カタログ ID** を指定します。ID はジェネレーターの登録済み一覧で確認できます。公開カタログをクライアントと共用できます。

```json
{
  "manifestUrl": "https://example.github.io/seiyo/manifest.json",
  "serverMods": ["example_mod"]
}
```

3. サーバーフォルダーで、次のように更新と起動を実行します。Fabric の JAR 名と起動オプションは既存のものに合わせてください。

```sh
java -jar seiyo-tools.jar server . -- java -Xmx4G -jar fabric-server-launch.jar nogui
```

起動コマンドを省くと、停止中のサーバーの Mod 更新だけを実行します。既存のサービス管理下では、停止後の起動前処理にも使えます。

```sh
java -jar seiyo-tools.jar server .
```

指定した項目とカタログの依存先のみを対象にします。クライアント専用 Mod とリソースパックはサーバーにインストールできません。取得と検証が全件成功してから JAR を差し替えます。失敗した場合は非ゼロで終了し、指定した起動コマンドは実行しません。差し替え中に失敗すると旧 JAR への復元を試みます。ワールドとサーバー設定は更新対象外です。

## 保存形式と復旧

- SYP はリソースパック ZIP の圧縮済みデータをそのまま保持します。形式変換で再圧縮せず、64 KiB 単位で読み戻します。読み戻したデータを確認できるように、元ファイルの SHA-256 をヘッダーに格納します。
- 起動中のリソースパック用一時ファイルは **.seiyo-updater/runtime-packs/** に置かれます。旧形式の Mod をブートストラップで読み込むときは **runtime-mods/** も使います。終了時に削除を試み、残った一時ファイルは次回起動時に作り直します。
- 差し替えに失敗した場合は旧ファイルに戻します。成功後も、以前導入していたファイルは **.seiyo-updater/job-…/** にバックアップとして残ります。以前の平文 JAR／ZIP から移行したときは、その旧バックアップも平文です。新たにダウンロードした元ファイルのバックアップは作りません。
- 公開する **manifest.json** の本文には DEFLATE 圧縮と可逆なマスクを適用します。Mod は同じ配布設定で受け入れた配布番号への巻き戻しを拒否します。旧設定で公開鍵を指定した場合は、引き続き署名の検証が必要です。
- 強制終了などで **pending** が残った場合は、直近の結果を **.seiyo-updater/last-result.txt** で確認し、Minecraft を完全に閉じた後、インスタンス直下の PowerShell で下記を実行すると適用を再開できます。

```powershell
$job = (Get-Content .seiyo-updater/pending -Raw).Trim()
java -cp ".seiyo-updater/$job/seiyo-update-worker.jar" jp.seiyo.updater.UpdateWorker "$($PWD.Path)" --recover
```

## ビルド

JDK 17 または 21 で、Windows は **.\gradlew.bat build**、macOS／Linux は **bash ./gradlew build** を実行します。**build/libs/** に クライアント用 Mod、ブートストラップ、seiyo-tools.jar が生成されます。
