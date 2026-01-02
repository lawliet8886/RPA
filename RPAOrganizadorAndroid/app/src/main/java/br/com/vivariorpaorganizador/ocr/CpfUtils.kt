package br.com.vivariorpaorganizador.ocr

object CpfUtils {
    fun isValid(cpfDigits: String): Boolean {
        val cpf = cpfDigits.filter { it.isDigit() }
        if (cpf.length != 11) return false
        if (cpf.all { it == cpf[0] }) return false
        val dv1 = calcDigit(cpf.substring(0,9), 10)
        val dv2 = calcDigit(cpf.substring(0,10), 11)
        return cpf[9].digitToInt() == dv1 && cpf[10].digitToInt() == dv2
    }

    private fun calcDigit(part: String, weightStart: Int): Int {
        var sum = 0
        var weight = weightStart
        for (ch in part) {
            sum += ch.digitToInt() * weight
            weight -= 1
        }
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }

    fun format(cpfDigits: String): String {
        val cpf = cpfDigits.filter { it.isDigit() }.padStart(11,'0').takeLast(11)
        return "${cpf.substring(0,3)}.${cpf.substring(3,6)}.${cpf.substring(6,9)}-${cpf.substring(9,11)}"
    }
}
