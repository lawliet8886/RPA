package br.com.vivariorpaorganizador.data

object Pis {
    // PIS/PASEP/NIT: 11 dígitos. Pesos: 3,2,9,8,7,6,5,4,3,2
    fun isValid(digits: String): Boolean {
        val d = digits.filter { it.isDigit() }
        if (d.length != 11) return false
        if ((0..9).any { ch -> d.all { it == ('0' + ch) } }) return false
        val weights = intArrayOf(3,2,9,8,7,6,5,4,3,2)
        var sum = 0
        for (i in 0 until 10) {
            sum += d[i].digitToInt() * weights[i]
        }
        val remainder = sum % 11
        val dv = if (remainder < 2) 0 else (11 - remainder)
        return dv == d[10].digitToInt()
    }

    fun format(digits: String): String {
        val d = digits.filter { it.isDigit() }
        if (d.length != 11) return digits
        return "${d.substring(0,3)}.${d.substring(3,8)}.${d.substring(8,10)}-${d.substring(10,11)}"
    }
}
