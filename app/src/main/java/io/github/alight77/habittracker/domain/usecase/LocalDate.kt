package io.github.alight77.habittracker.domain.usecase

import java.time.LocalDate

fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
