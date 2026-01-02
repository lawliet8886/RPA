package br.com.vivariorpaorganizador.data

object Cpf {
    fun isValid(digits: String): Boolean {
        val d = digits.filter { it.isDigit() }
        if (d.length != 11) return false
        if ((0..9).any { ch -> d.all { it == ('0' + ch) } }) return false

        fun calc(pos: Int): Int {
            var sum = 0
            var weight = pos + 1
            for (i in 0 until pos) {
                sum += (d[i].digitToInt() * weight)
                weight--
            }
            val mod = (sum * 10) % 11
            return if (mod == 10) 0 else mod
        }

        val dv1 = calc(9)
        val dv2 = run {
            var sum = 0
            var weight = 11
            for (i in 0 until 10) {
                sum += (d[i].digitToInt() * weight)
                weight--
            }
            val mod = (sum * 10) % 11
            if (mod == 10) 0 else mod
        }

        return dv1 == d[9].digitToInt() && dv2 == d[10].digitToInt()
    }

    fun format(digits: String): String {
        val d = digits.filter { it.isDigit() }
        if (d.length != 11) return digits
        return "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9,11)}"
    }
}
