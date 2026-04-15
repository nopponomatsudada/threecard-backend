package com.appmaster.domain.model

data class Area(
    val code: String,
    val nameJa: String,
    val nameEn: String
) {
    companion object {
        private val ALL = listOf(
            Area("01", "北海道", "Hokkaido"),
            Area("02", "青森県", "Aomori"),
            Area("03", "岩手県", "Iwate"),
            Area("04", "宮城県", "Miyagi"),
            Area("05", "秋田県", "Akita"),
            Area("06", "山形県", "Yamagata"),
            Area("07", "福島県", "Fukushima"),
            Area("08", "茨城県", "Ibaraki"),
            Area("09", "栃木県", "Tochigi"),
            Area("10", "群馬県", "Gunma"),
            Area("11", "埼玉県", "Saitama"),
            Area("12", "千葉県", "Chiba"),
            Area("13", "東京都", "Tokyo"),
            Area("14", "神奈川県", "Kanagawa"),
            Area("15", "新潟県", "Niigata"),
            Area("16", "富山県", "Toyama"),
            Area("17", "石川県", "Ishikawa"),
            Area("18", "福井県", "Fukui"),
            Area("19", "山梨県", "Yamanashi"),
            Area("20", "長野県", "Nagano"),
            Area("21", "岐阜県", "Gifu"),
            Area("22", "静岡県", "Shizuoka"),
            Area("23", "愛知県", "Aichi"),
            Area("24", "三重県", "Mie"),
            Area("25", "滋賀県", "Shiga"),
            Area("26", "京都府", "Kyoto"),
            Area("27", "大阪府", "Osaka"),
            Area("28", "兵庫県", "Hyogo"),
            Area("29", "奈良県", "Nara"),
            Area("30", "和歌山県", "Wakayama"),
            Area("31", "鳥取県", "Tottori"),
            Area("32", "島根県", "Shimane"),
            Area("33", "岡山県", "Okayama"),
            Area("34", "広島県", "Hiroshima"),
            Area("35", "山口県", "Yamaguchi"),
            Area("36", "徳島県", "Tokushima"),
            Area("37", "香川県", "Kagawa"),
            Area("38", "愛媛県", "Ehime"),
            Area("39", "高知県", "Kochi"),
            Area("40", "福岡県", "Fukuoka"),
            Area("41", "佐賀県", "Saga"),
            Area("42", "長崎県", "Nagasaki"),
            Area("43", "熊本県", "Kumamoto"),
            Area("44", "大分県", "Oita"),
            Area("45", "宮崎県", "Miyazaki"),
            Area("46", "鹿児島県", "Kagoshima"),
            Area("47", "沖縄県", "Okinawa"),
            Area("99", "その他", "Other")
        )

        fun findByCode(code: String): Area? = ALL.find { it.code == code }

        fun all(): List<Area> = ALL
    }
}
