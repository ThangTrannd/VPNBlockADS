package com.vpnblockads.core.model

object Domains {
    /** Chuẩn hoá: bỏ khoảng trắng, chữ thường, bỏ dấu chấm cuối ("Ads.COM." -> "ads.com"). */
    fun normalize(raw: String): String = raw.trim().lowercase().trimEnd('.')

    /**
     * Kiểm tra cú pháp domain (đã normalize): các label 1-63 ký tự [a-z0-9_-], không bắt đầu/kết thúc
     * bằng "-". Cho phép "_" vì một số domain quảng cáo dùng nó.
     *
     * Viết tay thay vì Regex: hàm này chạy cho ~100k dòng hosts lúc khởi động, Regex trên
     * Android chậm hơn nhiều lần (đo được ~10s trên emulator, bản debug).
     */
    fun isValid(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) return false
        var labelStart = 0
        for (i in 0..domain.length) {
            if (i == domain.length || domain[i] == '.') {
                val length = i - labelStart
                if (length == 0 || length > 63) return false
                if (domain[labelStart] == '-' || domain[i - 1] == '-') return false
                labelStart = i + 1
            } else {
                val c = domain[i]
                if (!(c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_')) return false
            }
        }
        return true
    }
}
