# 星陽アップデーター 1.2

Minecraft Java Edition 1.19.2／Fabric Loader 0.14.25 以降向けです。Java 17 以降が必要です。

## 参加者の初回設定

1. Minecraft を閉じます。星陽インスタンスの **mods/** に、**release/seiyo-updater-1.2.0+mc1.19.2.jar** を入れます。旧版のアップデーター JAR がある場合は入れ替えてください。
2. 配布元から受け取った **seiyo-updater.json** を、そのインスタンスの **config/** に入れます。タイトル画面の **星陽アップデート → 設定フォルダー** からも場所を開けます。
3. Mod も自動更新する場合は、**release/seiyo-bootstrap.jar** を星陽インスタンスの直下に置きます。ランチャーで星陽プロファイルの JVM 引数に、下記の例を使って引数を追加します。最後のパスはそのプロファイルのゲームディレクトリと一致させてください。ほかの JVM 引数は保持します。

```text
"-javaagent:D:/Minecraft/Seiyo/seiyo-bootstrap.jar=D:/Minecraft/Seiyo"
```

macOS の例です。

```text
"-javaagent:/Users/example/Minecraft/Seiyo/seiyo-bootstrap.jar=/Users/example/Minecraft/Seiyo"
```

ブートストラップは Fabric が Mod を探す前に動くため、**mods/** の外に置きます。アップデーター本体とブートストラップは手動で入れ替えます。リソースパックだけ更新する場合は JVM 引数の設定を省けます。

## 更新の流れ

1. タイトル画面から **星陽アップデート** を開きます。未導入・変換・更新の対象を確認します。必須項目と選択した項目の依存先は自動で選ばれます。
2. **取得してゲームを終了** を押します。JAR／ZIP を取得し、サイズ・SHA-256・アーカイブの情報を確認してから、拡張子 **.syp** に変換して保存します。ダウンロードした元の JAR／ZIP は検証後に削除されます。
3. ゲーム終了後にファイルを差し替えます。少し待ってランチャーから再起動してください。リソースパックはいつもの一覧に表示されます。初めて入れたものは一覧から有効にします。

管理対象の Mod は **mods/<Mod ID>.syp**、パックは **resourcepacks/<保存名>.zip.syp** に保存します。同じパックの更新では ZIP の保存名を固定すると、Minecraft の有効化状態を引き継げます。カタログから外した項目の既存ファイルは残ります。旧版から入っている通常の JAR／ZIP も更新時に変換できます。

## 管理者の配布手順

1. GitHub リポジトリに付属の **.github/workflows/pages.yml** と **site/** を置きます。リポジトリの **Settings → Pages → Source** で **GitHub Actions** を選びます。
2. **tools/seiyo-catalog-generator.html** をローカルで開き、公開する **manifest.json** の HTTPS URL を入力します。初回は署名鍵を作り、管理者鍵 **seiyo.admin.json** を手元に保管します。参加者用の設定には配布 URL と署名検証用の公開鍵が含まれます。
3. 元の JAR／ZIP と、その同じファイルを取得できる直接 HTTPS URL を登録します。ジェネレーターがサイズと SHA-256 を読み取ります。JAR では Mod ID・バージョンが自動入力されます。リソースパックには表示用のバージョンを入力します。
4. **manifest.json を出力**し、GitHub リポジトリの **site/manifest.json** に置いて既定ブランチへ反映します。Actions が **site/** を公開します。配布元ファイルはファイルごとの URL から取得するため、ジェネレーターが出力するのはメタデータだけです。
5. 次回は同じ署名鍵を開き、公開中の **manifest.json** を読み込んで更新します。配布番号は毎回増やしてください。旧形式の暗号化項目を読み込んだ場合は、元の JAR／ZIP と直接 URL を指定し直してから出力します。

新形式のカタログを公開する前に、参加者へアップデーター 1.2 を配ってください。1.2 は旧カタログを読み込めるため、クライアントの入れ替えを先に進められます。

共同開発用に公開済みの元ファイルは、直接 URL を知っていれば取得できます。**.syp** とカタログの難読化は、通常の ZIP ビューアーやテキストエディターでそのまま中身が見える状態を避けるためのものです。形式はソースに含まれます。アクセス制御や暗号化による保護は行いません。管理者の署名秘密鍵を公開リポジトリに含めないでください。

## 保存形式と復旧

- SYP は既存の JAR／ZIP の圧縮済みデータをそのまま保持します。形式変換で再圧縮せず、64 KiB 単位で読み戻します。変換前の署名済み SHA-256 と読み戻したデータの一致を確認できるように、元ファイルのハッシュをヘッダーに格納します。
- 起動中は Fabric／Minecraft が読める一時ファイルが **.seiyo-updater/runtime-mods/** と **runtime-packs/** に置かれます。終了時に削除を試み、Windows のファイルロックや強制終了で残った場合は次回起動時に消去して作り直します。
- 差し替えに失敗した場合は旧ファイルに戻します。成功後も、以前導入していたファイルは **.seiyo-updater/job-…/** にバックアップとして残ります。以前の平文 JAR／ZIP から移行したときは、その旧バックアップも平文です。新たにダウンロードした元ファイルのバックアップは作りません。
- 公開する **manifest.json** の本文は DEFLATE 圧縮と可逆なマスクを適用した上で署名します。Mod は署名を確認してから展開し、過去に受け入れた配布番号への巻き戻しを拒否します。
- 強制終了などで **pending** が残った場合は、直近の結果を **.seiyo-updater/last-result.txt** で確認し、Minecraft を完全に閉じた後、インスタンス直下の PowerShell で下記を実行すると適用を再開できます。

```powershell
$job = (Get-Content .seiyo-updater/pending -Raw).Trim()
java -cp ".seiyo-updater/$job/seiyo-update-worker.jar" jp.seiyo.updater.UpdateWorker "$($PWD.Path)" --recover
```

## ビルド

JDK 17 または 21 で、Windows は **.\gradlew.bat build**、macOS／Linux は **bash ./gradlew build** を実行します。**build/libs/** に Mod とブートストラップの2つの JAR が生成されます。
