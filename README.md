# 星陽アップデーター 1.3

Minecraft Java Edition 1.19.2／Fabric Loader 0.14.25 以降向けです。Java 17 以降が必要です。

## 参加者の初回設定

1. Minecraft を閉じます。星陽インスタンスの **mods/** に、**release/seiyo-updater-1.3.0+mc1.19.2.jar** を入れます。旧版のアップデーター JAR がある場合は入れ替えてください。
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
2. **tools/seiyo-catalog-generator.html** をローカルで開きます。JAR／ZIP を選び、同じファイルが取得できる直接 HTTPS URL を入力して **一覧に追加** を押します。サイズ・SHA-256・表示名を取得し、JAR では Mod ID とバージョンも読み取ります。リソースパックの初期バージョンには配布番号を使います。表示名などは必要に応じて詳細を開いて編集できます。
3. **manifest.json を出力**し、GitHub リポジトリの **site/manifest.json** に置いて既定ブランチへ反映します。Actions が **site/** を公開します。
4. 初回だけ **参加者用設定を作る（初回）** を開き、公開した **manifest.json** の HTTPS URL を入力して **seiyo-updater.json** を出力します。参加者用設定に必要なのは配布 URL だけです。
5. 次回は公開中の **manifest.json** を開いて項目を編集します。配布番号は自動で1つ進みます。旧版の署名付きカタログも一覧として開けます。旧形式の暗号化項目は、元の JAR／ZIP と直接 URL を指定し直してから出力してください。

署名なしへ移行する場合は、新カタログを公開する前に参加者へアップデーター 1.3 を配ってください。1.3 は旧設定で旧カタログを読み込めます。新カタログの公開時に参加者用の **seiyo-updater.json** を入れ替えます。

共同開発用に公開済みの元ファイルは、直接 URL から取得できます。**.syp** とカタログの難読化は可逆です。署名なしの配布では、指定した HTTPS 配布先から取得したカタログを信頼します。その配布先を書き換えられる人は、配布ファイルと照合用のハッシュも変更できます。

## 保存形式と復旧

- SYP は既存の JAR／ZIP の圧縮済みデータをそのまま保持します。形式変換で再圧縮せず、64 KiB 単位で読み戻します。読み戻したデータを確認できるように、元ファイルの SHA-256 をヘッダーに格納します。
- 起動中は Fabric／Minecraft が読める一時ファイルが **.seiyo-updater/runtime-mods/** と **runtime-packs/** に置かれます。終了時に削除を試み、Windows のファイルロックや強制終了で残った場合は次回起動時に消去して作り直します。
- 差し替えに失敗した場合は旧ファイルに戻します。成功後も、以前導入していたファイルは **.seiyo-updater/job-…/** にバックアップとして残ります。以前の平文 JAR／ZIP から移行したときは、その旧バックアップも平文です。新たにダウンロードした元ファイルのバックアップは作りません。
- 公開する **manifest.json** の本文には DEFLATE 圧縮と可逆なマスクを適用します。Mod は同じ配布設定で受け入れた配布番号への巻き戻しを拒否します。旧設定で公開鍵を指定した場合は、引き続き署名の検証が必要です。
- 強制終了などで **pending** が残った場合は、直近の結果を **.seiyo-updater/last-result.txt** で確認し、Minecraft を完全に閉じた後、インスタンス直下の PowerShell で下記を実行すると適用を再開できます。

```powershell
$job = (Get-Content .seiyo-updater/pending -Raw).Trim()
java -cp ".seiyo-updater/$job/seiyo-update-worker.jar" jp.seiyo.updater.UpdateWorker "$($PWD.Path)" --recover
```

## ビルド

JDK 17 または 21 で、Windows は **.\gradlew.bat build**、macOS／Linux は **bash ./gradlew build** を実行します。**build/libs/** に Mod とブートストラップの2つの JAR が生成されます。
