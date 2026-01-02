package br.com.vivariorpaorganizador.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object BusinessDays {

    fun nextBusinessDay(date: LocalDate): LocalDate {
        var d = date.plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    fun firstBusinessDayOfNextMonth(date: LocalDate): LocalDate {
        val ym = YearMonth.from(date).plusMonths(1)
        var d = ym.atDay(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    fun envioOsParaPeriodo(qualMetade: Int, anyShiftDate: LocalDate): LocalDate {
        // qualMetade: 1 = dias 1-15, 2 = dias 16-31
        return if (qualMetade == 1) {
            val base = LocalDate.of(anyShiftDate.year, anyShiftDate.month, 15)
            nextBusinessDay(base)
        } else {
            firstBusinessDayOfNextMonth(anyShiftDate)
        }
    }
}
