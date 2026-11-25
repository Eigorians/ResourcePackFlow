package com.eastcompany.eastsub.gacha.web.template

class WebHtmlTemplates {

    companion object {

        private fun escapeHtml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

        // =========================================================
        // Common
        // =========================================================

        fun renderPage(
            title: String,
            playerName: String?,
            bodyContent: String
        ): String {
            val userDisplay = renderUserMenu(playerName)

            return """
                <!DOCTYPE html>
                <html lang="ja">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">

                    <title>${escapeHtml(title)}</title>

                    <style>
                        * {
                            box-sizing: border-box;
                        }

                        body {
                            font-family:
                                -apple-system,
                                BlinkMacSystemFont,
                                "Segoe UI",
                                Roboto,
                                sans-serif;

                            margin: 0;
                            background: #f0f2f5;

                            display: flex;
                            flex-direction: column;

                            min-height: 100vh;
                        }

                        /* =========================
                           Header
                           ========================= */

                        header {
                            background: #ffffff;

                            padding: 15px 30px;

                            display: flex;
                            justify-content: space-between;
                            align-items: center;

                            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);

                            z-index: 100;
                        }

                        .logo {
                            font-weight: bold;
                            font-size: 18px;

                            color: #333333;
                            text-decoration: none;
                        }

                        .logo:hover {
                            color: #000000;
                        }

                        /* =========================
                           User Menu
                           ========================= */

                        .user-badge {
                            background: #e2f0d9;
                            color: #385723;

                            padding: 6px 14px;

                            border-radius: 20px;

                            font-size: 14px;
                            font-weight: bold;
                        }

                        .user-badge.guest {
                            background: #f8d7da;
                            color: #721c24;
                        }

                        .user-badge a {
                            color: #721c24;
                            text-decoration: underline;
                        }

                        .user-menu {
                            position: relative;
                        }

                        .user-menu > summary {
                            list-style: none;

                            cursor: pointer;
                            user-select: none;
                        }

                        .user-menu > summary::-webkit-details-marker {
                            display: none;
                        }

                        .user-menu > summary:hover {
                            background: #d4e8c8;
                        }

                        .user-menu-list {
                            position: absolute;

                            right: 0;
                            top: calc(100% + 6px);

                            min-width: 140px;

                            background: #ffffff;

                            border: 1px solid #dee2e6;
                            border-radius: 8px;

                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);

                            overflow: hidden;

                            z-index: 200;
                        }

                        .user-menu-list form {
                            margin: 0;
                        }

                        .user-menu-item {
                            display: block;

                            width: 100%;

                            padding: 10px 16px;

                            border: none;

                            background: #ffffff;
                            color: #dc3545;

                            font-size: 14px;
                            font-weight: bold;

                            text-align: left;

                            cursor: pointer;
                        }

                        .user-menu-item:hover {
                            background: #f8d7da;
                        }

                        /* =========================
                           Content
                           ========================= */

                        .content {
                            flex: 1;

                            display: flex;
                            justify-content: center;
                            align-items: flex-start;

                            padding: 20px;
                        }

                        .panel {
                            width: 100%;
                            max-width: 1100px;

                            background: #ffffff;

                            padding: 24px;

                            border-radius: 8px;

                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                        }

                        /* =========================
                           Navigation
                           ========================= */

                        .site-nav {
                            display: flex;
                            align-items: center;
                            gap: 6px;

                            margin-left: 24px;
                        }

                        .site-nav a {
                            padding: 7px 10px;

                            border-radius: 6px;

                            color: #57606a;

                            font-size: 14px;
                            font-weight: 600;

                            text-decoration: none;
                        }

                        .site-nav a:hover {
                            background: #f0f2f5;
                            color: #24292f;
                        }

                        .header-left {
                            display: flex;
                            align-items: center;
                        }

                        /* =========================
                           Responsive
                           ========================= */

                        @media (max-width: 700px) {
                            header {
                                padding: 12px 16px;
                            }

                            .site-nav {
                                display: none;
                            }

                            .content {
                                padding: 12px;
                            }

                            .panel {
                                padding: 18px;
                            }
                        }
                    </style>
                </head>

                <body>

                    <header>
                        <div class="header-left">

                            <a href="/home" class="logo">
                                ResourcePackFlow
                            </a>

                            <nav class="site-nav" aria-label="メインナビゲーション">
                                <a href="/home">ホーム</a>
                                <a href="/upload">新規登録</a>
                                <a href="/add-existing">既存アイテム</a>
                                <a href="/list">アイテム一覧</a>
                                <a href="/bulk-edit">一括設定</a>
                                <a href="/settings-io">設定の出力・取り込み</a>
                                <a href="/history">ガチャ履歴</a>
                            </nav>

                        </div>

                        $userDisplay
                    </header>

                    <main class="content">
                        <div class="panel">
                            $bodyContent
                        </div>
                    </main>

                    <script>
                        (function () {
                            const menu =
                                document.getElementById('userMenu');

                            if (!menu) {
                                return;
                            }

                            document.addEventListener(
                                'click',
                                function (event) {
                                    if (!menu.contains(event.target)) {
                                        menu.removeAttribute('open');
                                    }
                                }
                            );

                            document.addEventListener(
                                'keydown',
                                function (event) {
                                    if (event.key === 'Escape') {
                                        menu.removeAttribute('open');
                                    }
                                }
                            );
                        })();
                    </script>

                </body>
                </html>
            """.trimIndent()
        }

        // =========================================================
        // User
        // =========================================================

        private fun renderUserMenu(
            playerName: String?
        ): String {
            if (playerName.isNullOrBlank()) {
                return """
                    <div class="user-badge guest">
                        未ログイン
                        (<a href="/login">ログイン</a>)
                    </div>
                """.trimIndent()
            }

            return """
                <details class="user-menu" id="userMenu">
                    <summary class="user-badge">
                        👤 ${escapeHtml(playerName)} ▾
                    </summary>

                    <div class="user-menu-list">
                        <form method="post" action="/logout">
                            <button
                                type="submit"
                                class="user-menu-item"
                            >
                                ログアウト
                            </button>
                        </form>
                    </div>
                </details>
            """.trimIndent()
        }
    }
}