package me.andreaiacono.importer

import java.time.LocalDate
import java.time.format.DateTimeFormatter


val homeBankDateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")!!
val eticaDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")!!

class Transaction(
        account: String,
        currency: String,
        date: LocalDate,
        startBalance: Float,
        endBalance: Float,
        val opDate: String,
        var amount: Float,
        var description: String,
        categories: Map<String, List<Triple<String, String, Int>>>,
        defaultPayment: Int)
{

    var category: String
    var payment = defaultPayment

    init {
        category = "Altro"
        if (amount > 0) {
            category = "Entrate"
        } else if (description.startsWith("GEA") || description.startsWith("PREL.BANCOMAT")) {  // POS withdrawal
            category = "Prelievo"
            description = "Prelievo " + amount.toString().substring(1) + "€"
            amount = 0f
        } else if (description.contains("INT CARD SERVICES") || description.contains("CARTASI S.P.A.")) {  // Credit card payment (not counted as an operation)
            category = "Altro"
            description = "Pagamento carta di credito: €" + amount.toString().substring(1)
            amount = 0f
        } else if (description.contains("Land: ??")) { // POS from abroad
            category = "Viaggi"
            description = "Grecia: " + description.substring(33)
        }
        for (cat in categories.keys) {
            for (regexp in categories[cat].orEmpty()) {
                if (description.toLowerCase().contains(regexp.first.toLowerCase())) {
                    category = cat
                    description = regexp.second
                    payment = regexp.third
                }
            }
        }

        if (category == "Altro") {
            println("$opDate ($date) [$amount] Category not found for [$description]")
        }
    }

}

fun fromAbnCsv(fields: List<String>, categories: Map<String, List<Triple<String, String, Int>>>, defaultPayment: Int) : Transaction {

    val account: String = fields[0]
    val currency = fields[1]
    val date = LocalDate.parse(fields[2], DateTimeFormatter.BASIC_ISO_DATE)
    val startBalance = fields[3].replace(',', '.').toFloat()
    val endBalance = fields[4].replace(',', '.').toFloat()
    val opDate = LocalDate.parse(fields[5], DateTimeFormatter.BASIC_ISO_DATE).format(homeBankDateFormatter)
    val amount = fields[6].replace(',', '.').toFloat()
    val description = fields[7].substring(33)

    return Transaction(account, currency, date, startBalance, endBalance, opDate, amount, description, categories, defaultPayment)
}

fun fromBancaEticaCsv(fields: List<String>, categories: Map<String, List<Triple<String, String, Int>>>, defaultPayment: Int) : Transaction {

    val opDate = LocalDate.parse(fields[1], eticaDateFormatter).format(homeBankDateFormatter)
    val account = ""
    val currency = "EUR"
    val date = LocalDate.parse(fields[1], eticaDateFormatter)
    val startBalance = fields[5].replace(".", "").replace(',', '.').toFloat()
    val endBalance = fields[5].replace(".", "").replace(',', '.').toFloat()
    val amountValue = if (fields[3] == "") "-"+fields[4] else fields[3]
    val amount = amountValue.replace(".", "").replace(",", ".").toFloat()
    val description = fields[2]

    return Transaction(account, currency, date, startBalance, endBalance, opDate, amount, description, categories, defaultPayment)
}


fun toCsv(tx: Transaction) = "${tx.opDate};${tx.payment};;;${tx.description};${tx.amount};${tx.category};"
