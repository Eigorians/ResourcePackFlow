package com.eastcompany.eastsub.gacha.web.page

/** ホーム画面 (/home) のHTML */
object PackHomePage {

    private data class MenuItem(
        val number: String,
        val title: String,
        val description: String,
        val detail: String,
        val path: String
    )

    private val menuItems = listOf(
        MenuItem(
            number = "01",
            title = "新規アイテム登録",
            description = "画像・3Dモデルから作成",
            detail = "画像や3Dモデルを取り込み、ガチャで使用するアイテムを登録します。",
            path = "/upload"
        ),
        MenuItem(
            number = "02",
            title = "既存アイテムを追加",
            description = "配置済みのモデル・テクスチャを使用",
            detail = "SSHでリソースパックに配置済みのモデルやテクスチャを選び、ガチャに追加します。",
            path = "/add-existing"
        ),
        MenuItem(
            number = "03",
            title = "アイテム一覧・設定",
            description = "登録内容・排出設定を管理",
            detail = "登録済みアイテムの確認や、レア度・排出確率などの設定を行います。",
            path = "/list"
        ),
        MenuItem(
            number = "04",
            title = "ガチャ履歴",
            description = "プレイヤーのガチャ結果・収益確認",
            detail = "あなたのガチャが回された履歴や、レア度ごとの内訳、総収益を確認します。",
            path = "/history"
        )
    )


    fun renderBody(): String {
        val cards = menuItems.joinToString("\n") { item ->
            renderMenuCard(item)
        }

        return """
            <style>
                .home-container {
                    width: 100%;
                    max-width: 880px;
                    margin: 0 auto;
                    padding: 20px 0 48px;
                    color: #24292f;
                    box-sizing: border-box;
                }

.home-header {
    margin-bottom: 28px;
}

.home-heading {
    margin: 0;
    color: #1f2328;
    font-size: 1.6rem;
    font-weight: 700;
    letter-spacing: -0.04em;
    line-height: 1.5;
}

.home-heading {
    margin: 0;
    color: #1f2328;
    font-size: 1.75rem;
    font-weight: 700;
    line-height: 1.4;
    letter-spacing: -0.04em;
}

.home-intro {
    margin: 8px 0 0;
    color: #656d76;
    font-size: 0.875rem;
    line-height: 1.8;
}

                .section-heading {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    margin: 0 0 16px;
                    color: #656d76;
                    font-size: 0.75rem;
                    font-weight: 700;
                    letter-spacing: 0.12em;
                }

                .section-heading::after {
                    content: "";
                    flex: 1;
                    height: 1px;
                    background: #eaeef2;
                }

                .home-menu-grid {
                    display: grid;
                    grid-template-columns: repeat(2, minmax(0, 1fr));
                    gap: 14px;
                }

                .menu-card {
                    position: relative;
                    display: flex;
                    flex-direction: column;
                    min-width: 0;
                    min-height: 218px;
                    padding: 22px;
                    box-sizing: border-box;
                    color: #24292f;
                    text-decoration: none;
                    background: #ffffff;
                    border: 1px solid #d8dee4;
                    border-radius: 12px;
                    transition:
                        border-color 160ms ease,
                        background-color 160ms ease,
                        transform 160ms ease,
                        box-shadow 160ms ease;
                }

                .menu-card:hover {
                    color: #24292f;
                    background: #fbfcfe;
                    border-color: #afb8c1;
                    box-shadow: 0 5px 18px rgba(31, 35, 40, 0.05);
                    transform: translateY(-2px);
                }

                .menu-card:focus-visible {
                    outline: 3px solid #0969da;
                    outline-offset: 3px;
                }

                .card-top {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: 27px;
                }

                .card-number {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    min-width: 34px;
                    height: 27px;
                    padding: 0 8px;
                    box-sizing: border-box;
                    color: #57606a;
                    background: #f6f8fa;
                    border: 1px solid #eaeef2;
                    border-radius: 6px;
                    font-size: 0.7rem;
                    font-weight: 700;
                    letter-spacing: 0.06em;
                }

                .card-arrow {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    width: 30px;
                    height: 30px;
                    color: #8c959f;
                    border: 1px solid transparent;
                    border-radius: 7px;
                    font-size: 1.1rem;
                    transition:
                        color 160ms ease,
                        background-color 160ms ease,
                        transform 160ms ease;
                }

                .menu-card:hover .card-arrow {
                    color: #24292f;
                    background: #f0f3f6;
                    transform: translate(2px, -2px);
                }

                .card-title {
                    font-size: 1rem;
                    font-weight: 700;
                    line-height: 1.6;
                    letter-spacing: -0.025em;
                    overflow-wrap: anywhere;
                }

                .card-subtitle {
                    margin-top: 5px;
                    color: #57606a;
                    font-size: 0.78rem;
                    font-weight: 600;
                    line-height: 1.7;
                    overflow-wrap: anywhere;
                }

                .card-detail {
                    margin-top: 13px;
                    color: #656d76;
                    font-size: 0.8rem;
                    line-height: 1.9;
                    overflow-wrap: anywhere;
                }

                @media (max-width: 600px) {
                    .home-container {
                        padding: 12px 0 30px;
                    }

                    .home-header {
                        margin-bottom: 28px;
                    }

                    .home-menu-grid {
                        grid-template-columns: 1fr;
                        gap: 10px;
                    }

                    .menu-card {
                        min-height: 0;
                        padding: 19px;
                    }

                    .card-top {
                        margin-bottom: 17px;
                    }

                    .card-detail {
                        margin-top: 9px;
                    }
                }

                @media (prefers-reduced-motion: reduce) {
                    .menu-card,
                    .card-arrow {
                        transition: none;
                    }

                    .menu-card:hover,
                    .menu-card:hover .card-arrow {
                        transform: none;
                    }
                }
            </style>

            <main class="home-container">

                <section aria-label="アイテム管理メニュー">
                    <h3 class="section-heading">MENU</h3>

                    <nav
                        class="home-menu-grid"
                        aria-label="メインメニュー"
                    >
                        $cards
                    </nav>
                </section>
            </main>
        """.trimIndent()
    }

    private fun renderMenuCard(item: MenuItem): String {
        return """
            <a
                class="menu-card"
                href="${escapeHtml(item.path)}"
            >
                <div class="card-top">
                    <span class="card-number">
                        ${escapeHtml(item.number)}
                    </span>

                    <span class="card-arrow" aria-hidden="true">
                        ↗
                    </span>
                </div>

                <div class="card-title">
                    ${escapeHtml(item.title)}
                </div>

                <div class="card-subtitle">
                    ${escapeHtml(item.description)}
                </div>

                <div class="card-detail">
                    ${escapeHtml(item.detail)}
                </div>
            </a>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
