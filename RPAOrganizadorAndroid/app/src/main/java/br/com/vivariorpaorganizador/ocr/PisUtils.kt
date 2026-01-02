package br.com.vivariorpaorganizador.ocr

object PisUtils {
    private val weights = intArrayOf(3,2,9,8,7,6,5,4,3,2)

    fun isValid(pisDigits: String): Boolean {
        val pis = pisDigits.filter { it.isDigit() }
        if (pis.length != 11) return false
        val base = pis.substring(0,10)
        val dv = pis[10].digitToInt()
        var sum = 0
        for (i in 0 until 10) {
            sum += base[i].digitToInt() * weights[i]
        }
        val mod = sum % 11
        val calc = if (mod < 2) 0 else 11 - mod
        return dv == calc
    }

    fun format(pisDigits: String): String {
        val pis = pisDigits.filter { it.isDigit() }.padStart(11,'0').takeLast(11)
        // Formato comum: 000.00000.00-0
        return "${pis.substring(0,3)}.${pis.substring(3,8)}.${pis.substring(8,10)}-${pis.substring(10,11)}"
    }
}
