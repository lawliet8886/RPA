# RPA Organizador (Android) – v0.2.0

Este projeto é um **organizador de RPAs** pensado para o fluxo do CAPS (anexar documentos, rodar OCR, apontar pendências e exportar um ZIP no padrão que você pediu).

## O que já está implementado (v0.2.0)

- Cadastro de profissionais
- Cadastro de plantões (data, horas, turno)
- Anexo de documentos (PDF/imagem) com permissão persistente (SAF)
- OCR (ML Kit) em:
  - **imagens**
  - **PDFs multipágina** (renderiza cada página e reconhece o texto)
- Extração automática (heurística) de:
  - CPF (com validação)
  - PIS/PASEP (com validação)
  - Agência/Conta/Titular (heurística)
  - COREN (número + validade, quando aparece)
  - Certidão “Nada Consta” (mês/ano)
  - Data do comprovante de residência (quando aparece)
- Motor de **pendências** conforme regras definidas:
  - Obrigatórios: CPF + PIS/PASEP
  - Comprovante de residência: < 90 dias; se não nominal, pede carta + docs
  - Banco: agência + conta + titular
  - COREN na validade
  - Certidão negativa do COREN válida no mês atual
- Tela de “sanar pendência”: botão que aponta exatamente qual doc anexar
- Exportação de **ZIP** no cache + compartilhamento:
  - `RPA/`
  - `RPA/CONTROLE_PAGAMENTO.xlsx` (gerado por escritor OpenXML mínimo)
  - `RPA/PROFISSIONAIS/<PROF>/DOCUMENTOS/...`
  - OS em `DOCX` (preenche `template_os.docx` do assets)
  - OS em `PDF` (resumo)
  - `CHECKLIST_DOCUMENTOS.txt`

> Observação: o `.xlsx` é um writer mínimo (inline strings). Abre no Excel/Google Sheets e é suficiente para controle.

## Como compilar

1. Abra a pasta no Android Studio
2. Sincronize Gradle
3. Rode em um dispositivo físico (recomendado) ou emulador

## Próximos passos (o que dá pra evoluir)

- Importar a tabela oficial de valores (XLSX) e calcular automaticamente por função/turno
- Endereço do profissional via OCR + edição manual
- Conversão do DOCX para PDF com layout idêntico (hoje o PDF é um resumo)
- Exportar também para uma pasta fixa (Downloads/Servidor) via SAF
