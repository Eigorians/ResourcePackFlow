package com.eastcompany.eastsub.gacha.web.page

/** ログイン画面 (/login) のHTML */
object PackLoginPage {

    /** ワンタイムパスワードの入力フォーム */
    fun renderForm(): String = """
        <!DOCTYPE html>
        <html lang="ja">
        <head>
            <meta charset="utf-8">
            <title>ResourcePackFlow Login</title>
            <style>
                body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; background: #f0f2f5; margin: 0; }
                .login-box { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); width: 300px; text-align: center; }
                input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px; }
                button { width: 100%; padding: 10px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; }
                button:hover { background: #0056b3; }
            </style>
        </head>
        <body>
            <div class="login-box">
                <h2>ResourcePackFlow</h2>
                <form action="/login" method="post">
                    <input type="password" name="password" placeholder="ワンタイムパスワードを入力" required>
                    <button type="submit">ログイン</button>
                </form>
            </div>
        </body>
        </html>
    """.trimIndent()

    /** パスワードが無効だった時 */
    fun renderFailed(): String = """
        <!DOCTYPE html>
        <html lang="ja">
        <head><meta charset="utf-8"><title>Login Failed</title></head>
        <body style="font-family: sans-serif; text-align: center; margin-top: 50px;">
            <h3 style="color: red;">パスワードが無効です（既に使われたか、期限切れです）</h3>
            <a href="/login">戻る</a>
        </body>
        </html>
    """.trimIndent()
}
