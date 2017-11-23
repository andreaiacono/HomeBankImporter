package me.andreaiacono.importer

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val INPUT_FILE = "src/main/resources/input_data.tab"
const val OUTPUT_FILE = "src/main/resources/output_data.csv"

val dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")!!
val defaultPayment = 6


fun main(args: Array<String>) {

    val dataRetriever = DataRetriever()
    // writes the last transactions downloaded from the bank on disk
    File(INPUT_FILE).writeText(dataRetriever.getLastBankTransactions())
//    File(INPUT_FILE).writeText(getLastCreditCardTransactions())

    val categories = loadCategories()

    // writes an output files based on reading the input file and transforming every row into CSV format
    File(OUTPUT_FILE).writeText(
            File(INPUT_FILE)
                    .readLines()
                    .map { transformCsv(Transaction(it.split("\t"), categories)) }
                    .joinToString("\n")
    )
}


fun loadCategories(): Map<String, List<Triple<String, String, Int>>> {
    val lines = File("src/main/resources/categories.txt").readLines()
    val categories: MutableMap<String, List<Triple<String, String, Int>>> = mutableMapOf()
    var lastCategory = ""
    for (line in lines) {
        val splittedLine = line.split(":")
        var name: String
        if (!line.startsWith("\t")) {
            lastCategory = splittedLine[0]
            name = lastCategory
        } else {
            name = lastCategory + ":" + splittedLine[0].trim()
        }
        if (splittedLine.size > 1) {
            val names = line.split(":")[1].split(";").map { it.trim() }
            val regexps = names.map { loadCategory(it) }
            categories.put(name, regexps)
        } else {
            categories.put(name, emptyList())
        }
    }

    println(categories)
    return categories
}


fun loadCategory(line: String): Triple<String, String, Int> {
    var payment = defaultPayment
    return if (line.indexOf('[') < 0) {
        Triple(line, line, payment)
    } else {
        val matcher = line.split("[")[0]
        var description = line.split("[")[1].dropLast(1)
        if (description.indexOf("|") > 0) {
            payment = description.split("|")[1].trim().toInt()
            description = description.split("|")[0]
        }
        Triple(matcher, description, payment)
    }
}


fun transformCsv(tx: Transaction) = "${tx.opDate};${tx.payment};;;${tx.description};${tx.amount};${tx.category};"


class Transaction(fields: List<String>, categories: Map<String, List<Triple<String, String, Int>>>) {
    val account: String = fields[0]
    val currency = fields[1]
    val date = LocalDate.parse(fields[2], DateTimeFormatter.BASIC_ISO_DATE)
    val startBalance = fields[3].replace(',', '.').toFloat()
    val endBalance = fields[4].replace(',', '.').toFloat()
    val opDate = LocalDate.parse(fields[5], DateTimeFormatter.BASIC_ISO_DATE).format(dateFormatter)
    var amount = fields[6].replace(',', '.').toFloat()
    var description = fields[7].substring(33)
    var category: String
    var payment = defaultPayment

    init {
        category = "Altro"
        if (amount > 0) {
            category = "Entrate"
        } else if (fields[7].startsWith("GEA")) {  // POS withdrawal
            category = "Prelievo"
            description = "Prelievo " + amount.toString().substring(1) + "€"
            amount = 0f
        } else if (fields[7].contains("INT CARD SERVICES")){  // Credit card payment (not counted as an operation)
            category = "Altro"
            description = "Pagamento carta di credito: €" + amount.toString().substring(1)
            amount = 0f

        } else if (fields[7].contains("Land: ??")) { // POS from abroad
            category = "Viaggi"
            description = "Grecia: " + fields[7].substring(33)
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
