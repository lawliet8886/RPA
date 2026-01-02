package br.com.vivariorpaorganizador.ui

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vivariorpaorganizador.MainViewModel
import br.com.vivariorpaorganizador.data.*
import br.com.vivariorpaorganizador.export.RpaZipExporter
import br.com.vivariorpaorganizador.export.ShareUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAdd: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenProfessional: (String) -> Unit
) {
    val st by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RPA Organizador") },
                actions = {
                    TextButton(onClick = onOpenPrices) { Text("Tabela") }
                    TextButton(onClick = {
                        scope.launch {
                            val file = RpaZipExporter.exportToCacheZip(ctx, st)
                            ShareUtils.shareZip(ctx, file)
                        }
                    }) { Text("Exportar") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Text("+") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(st.professionals) { p ->
                val pend = PendencyEngine.compute(st, p)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfessional(p.id) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.nome.ifBlank { "(sem nome)" },
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (pend.isNotEmpty()) {
                                Badge { Text(pend.size.toString()) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.funcao, style = MaterialTheme.typography.bodySmall)
                        val cpf = p.extracted.cpf.ifBlank { "CPF: —" }
                        val pis = p.extracted.pisPasep.ifBlank { "PIS: —" }
                        Text("$cpf | $pis", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (st.professionals.isEmpty()) {
                item { Text("Nenhum profissional ainda. Use o + pra cadastrar.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfessionalScreen(vm: MainViewModel, onDone: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var funcao by remember { mutableStateOf("Enfermeiro (plantonista) 40h") }

    val options = listOf(
        "Enfermeiro (plantonista) 40h",
        "Téc. de Enfermagem (plantonista) 40h",
        "Outro"
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Novo profissional") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = funcao,
                    onValueChange = { funcao = it },
                    label = { Text("Função") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = false
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                funcao = if (opt == "Outro") "" else opt
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (nome.trim().isNotEmpty()) {
                            vm.addProfessional(nome.trim(), funcao.ifBlank { "Outro" })
                            onDone()
                        }
                    }
                ) { Text("Salvar") }

                OutlinedButton(onClick = onDone) { Text("Cancelar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val uiMsg by vm.uiMessage.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiMsg) {
        if (!uiMsg.isNullOrBlank()) {
            snackbar.showSnackbar(uiMsg!!)
            vm.clearUiMessage()
        }
    }

    val importXlsx = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) vm.importPriceTableFromFile(uri, replaceExisting = true)
        }
    )
    var funcao by remember { mutableStateOf("Enfermeiro (plantonista) 40h") }
    var tipo by remember { mutableStateOf(ShiftType.NOITE) }
    var horas by remember { mutableStateOf("12") }
    var valor by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabela de preços") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.importOfficialPriceTableFromAssets(replaceExisting = true) }) {
                    Text("Importar tabela oficial")
                }
                OutlinedButton(onClick = { importXlsx.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
                )) }) {
                    Text("Importar XLSX")
                }
            }

            OutlinedTextField(value = funcao, onValueChange = { funcao = it }, label = { Text("Função") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tipo == ShiftType.DIA, onClick = { tipo = ShiftType.DIA }, label = { Text("Dia") })
                FilterChip(selected = tipo == ShiftType.NOITE, onClick = { tipo = ShiftType.NOITE }, label = { Text("Noite") })
            }

            OutlinedTextField(value = horas, onValueChange = { horas = it }, label = { Text("Horas (ex.: 12)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = valor, onValueChange = { valor = it }, label = { Text("Valor do plantão (R$)") }, modifier = Modifier.fillMaxWidth())

            Button(onClick = {
                val h = horas.toIntOrNull() ?: 0
                val v = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
                if (h > 0) vm.upsertPriceRule(funcao.trim(), tipo, h, v)
            }) { Text("Salvar regra") }

            Spacer(Modifier.height(12.dp))
            Text("Regras cadastradas", fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.priceRules) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.funcao, fontWeight = FontWeight.SemiBold)
                            Text("${r.hours}h • ${r.type} • R$ ${String.format(Locale.getDefault(), "%.2f", r.value)}")
                        }
                    }
                }
                if (st.priceRules.isEmpty()) {
                    item { Text("Nenhuma regra ainda. Cadastre pelo menos a de 12h noite.") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDetailScreen(vm: MainViewModel, profId: String, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val ctx = LocalContext.current
    val prof = st.professionals.find { it.id == profId }

    if (prof == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profissional") },
                    navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Profissional não encontrado")
            }
        }
        return
    }

    var showEdit by remember { mutableStateOf(false) }

    val pend = remember(st, prof) { PendencyEngine.compute(st, prof) }
    val shifts = st.shifts.filter { it.professionalId == profId }.sortedByDescending { it.dateIso }
    val atts = st.attachments.filter { it.professionalId == profId }

    val pickers = rememberAttachmentPickers(
        context = ctx,
        onPicked = { category, uri, name, mime -> vm.attachDocument(profId, category, uri, name, mime) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(prof.nome.ifBlank { "Profissional" }) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
                actions = { TextButton(onClick = { showEdit = true }) { Text("Editar") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(prof.funcao, style = MaterialTheme.typography.bodySmall)
                        Text("CPF: ${prof.extracted.cpf.ifBlank { "—" }}")
                        Text("PIS: ${prof.extracted.pisPasep.ifBlank { "—" }}")
                        Text(
                            "Banco: ${prof.extracted.bancoAgencia.ifBlank { "—" }} / ${prof.extracted.bancoConta.ifBlank { "—" }} / ${prof.extracted.bancoTitular.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "COREN: ${prof.extracted.corenNumero.ifBlank { "—" }} • Validade: ${prof.extracted.corenValidade.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Nada Consta: ${prof.extracted.corenNadaConstaMesAno.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Comprovante: ${prof.extracted.comprovanteDataEmissao.ifBlank { "—" }} • Nominal: ${prof.extracted.comprovanteEhNominal?.toString() ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("Pendências", fontWeight = FontWeight.SemiBold)
                if (pend.isEmpty()) {
                    Text("Nenhuma pendência. Tá redondo.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pend.forEach { pe ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pe.title, fontWeight = FontWeight.SemiBold)
                                        Text(pe.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = {
                                        when (pe.action) {
                                            PendencyAction.ATTACH_CPF -> pickers.open(DocCategory.CPF)
                                            PendencyAction.ATTACH_PIS -> pickers.open(DocCategory.PIS_PASEP)
                                            PendencyAction.ATTACH_RESIDENCIA -> pickers.open(DocCategory.COMPROVANTE_RESIDENCIA)
                                            PendencyAction.ATTACH_CARTA_TERCEIROS -> pickers.open(DocCategory.CARTA_RESIDENCIA_TERCEIROS)
                                            PendencyAction.ATTACH_DOC_TITULAR -> pickers.open(DocCategory.DOC_TITULAR_COMPROVANTE)
                                            PendencyAction.ATTACH_DOC_PROF -> pickers.open(DocCategory.DOC_PROFISSIONAL)
                                            PendencyAction.ATTACH_BANCO -> pickers.open(DocCategory.BANCO)
                                            PendencyAction.ATTACH_COREN -> pickers.open(DocCategory.COREN)
                                            PendencyAction.ATTACH_NADA_CONSTA -> pickers.open(DocCategory.NADA_CONSTA_COREN)
                                            PendencyAction.EDIT_FIELDS -> showEdit = true
                                        }
                                    }) { Text("Sanar") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Documentos anexados", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocCategory.values().forEach { cat ->
                        val has = atts.any { it.category == cat }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cat.pretty(), fontWeight = FontWeight.SemiBold)
                                    Text(if (has) "Anexado" else "Não anexado", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { pickers.open(cat) }) { Text(if (has) "Trocar" else "Anexar") }
                            }
                        }
                    }
                }
            }

            item {
                Text("Plantões", fontWeight = FontWeight.SemiBold)
                AddShiftInline(onAdd = { dateIso, hours, type -> vm.addShift(profId, dateIso, hours, type) })
            }

            items(shifts) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatDateIso(s.dateIso)} • ${s.hours}h • ${s.type}", fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { vm.removeShift(s.id) }) { Text("Remover") }
                    }
                }
            }

            if (shifts.isEmpty()) {
                item { Text("Nenhum plantão lançado ainda.") }
            }
        }
    }

    if (showEdit) {
        EditExtractedDialog(
            prof = prof,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                vm.updateProfessional(updated)
                showEdit = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShiftInline(onAdd: (String, Int, ShiftType) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var hours by remember { mutableStateOf("12") }
    var type by remember { mutableStateOf(ShiftType.NOITE) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Horas") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == ShiftType.DIA, onClick = { type = ShiftType.DIA }, label = { Text("Dia") })
                FilterChip(selected = type == ShiftType.NOITE, onClick = { type = ShiftType.NOITE }, label = { Text("Noite") })
            }
            Button(onClick = {
                val h = hours.toIntOrNull() ?: return@Button
                onAdd(date, h, type)
            }) { Text("Adicionar plantão") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExtractedDialog(prof: Professional, onDismiss: () -> Unit, onSave: (Professional) -> Unit) {
    val p = prof.copy(extracted = prof.extracted.copy(), manual = prof.manual.copy())

    var cpf by remember { mutableStateOf(p.extracted.cpf) }
    var pis by remember { mutableStateOf(p.extracted.pisPasep) }
    var agencia by remember { mutableStateOf(p.extracted.bancoAgencia) }
    var conta by remember { mutableStateOf(p.extracted.bancoConta) }
    var titular by remember { mutableStateOf(p.extracted.bancoTitular) }
    var compDate by remember { mutableStateOf(p.extracted.comprovanteDataEmissao) }
    var compNominal by remember { mutableStateOf(p.extracted.comprovanteEhNominal) }
    var corenNum by remember { mutableStateOf(p.extracted.corenNumero) }
    var corenVal by remember { mutableStateOf(p.extracted.corenValidade) }
    var nada by remember { mutableStateOf(p.extracted.corenNadaConstaMesAno) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                p.extracted.cpf = cpf
                p.extracted.pisPasep = pis
                p.extracted.bancoAgencia = agencia
                p.extracted.bancoConta = conta
                p.extracted.bancoTitular = titular
                p.extracted.comprovanteDataEmissao = compDate
                p.extracted.comprovanteEhNominal = compNominal
                p.extracted.corenNumero = corenNum
                p.extracted.corenValidade = corenVal
                p.extracted.corenNadaConstaMesAno = nada

                // Quando editar manualmente, marca como confirmado
                if (cpf.isNotBlank()) p.manual.cpfConfirmado = true
                if (pis.isNotBlank()) p.manual.pisConfirmado = true
                if (agencia.isNotBlank() && conta.isNotBlank() && titular.isNotBlank()) p.manual.bancoConfirmado = true
                if (compDate.isNotBlank()) p.manual.comprovanteConfirmado = true
                if (corenVal.isNotBlank()) p.manual.corenConfirmado = true
                if (nada.isNotBlank()) p.manual.nadaConstaConfirmado = true

                onSave(p)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Editar dados extraídos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cpf, { cpf = it }, label = { Text("CPF") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pis, { pis = it }, label = { Text("PIS/PASEP") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(agencia, { agencia = it }, label = { Text("Agência") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conta, { conta = it }, label = { Text("Conta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(titular, { titular = it }, label = { Text("Titular") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(compDate, { compDate = it }, label = { Text("Data emissão comprovante (dd/MM/yyyy)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comprovante nominal?")
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = compNominal == true, onClick = { compNominal = true }, label = { Text("Sim") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = compNominal == false, onClick = { compNominal = false }, label = { Text("Não") })
                }
                OutlinedTextField(corenNum, { corenNum = it }, label = { Text("COREN") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(corenVal, { corenVal = it }, label = { Text("Validade COREN (dd/MM/yyyy)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nada, { nada = it }, label = { Text("Nada Consta (MM/yyyy)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

private fun DocCategory.pretty(): String = when (this) {
    DocCategory.CPF -> "CPF"
    DocCategory.PIS_PASEP -> "PIS/PASEP"
    DocCategory.COMPROVANTE_RESIDENCIA -> "Comprovante de residência"
    DocCategory.CARTA_RESIDENCIA_TERCEIROS -> "Carta (residência em nome de terceiros)"
    DocCategory.DOC_TITULAR_COMPROVANTE -> "Documento do titular do comprovante"
    DocCategory.DOC_PROFISSIONAL -> "Documento do profissional"
    DocCategory.BANCO -> "Banco (ag/conta/titular)"
    DocCategory.COREN -> "COREN"
    DocCategory.NADA_CONSTA_COREN -> "Nada Consta COREN"
}

private fun formatDateIso(iso: String): String {
    return try {
        val d = LocalDate.parse(iso)
        d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        iso
    }
}

private data class PickerBundle(val open: (DocCategory) -> Unit)

@Composable
private fun rememberAttachmentPickers(
    context: Context,
    onPicked: (DocCategory, Uri, String, String) -> Unit
): PickerBundle {
    var currentCategory by remember { mutableStateOf(DocCategory.CPF) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val cr = context.contentResolver
            try {
                cr.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = queryDisplayName(cr, uri) ?: "arquivo"
            val mime = cr.getType(uri) ?: "application/octet-stream"
            onPicked(currentCategory, uri, name, mime)
        }
    }

    fun open(cat: DocCategory) {
        currentCategory = cat
        launcher.launch(arrayOf("application/pdf", "image/*"))
    }

    return PickerBundle(::open)
}

private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = cr.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else null
    } catch (_: Exception) {
        null
    } finally {
        cursor?.close()
    }
}
