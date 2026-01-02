package br.com.vivariorpaorganizador.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
