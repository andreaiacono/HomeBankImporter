package me.andreaiacono.importer

import com.opencsv.CSVReader
import java.io.File
import java.io.FileReader
import java.lang.System.exit


const val OUTPUT_FILE = "src/main/resources/output_data.csv"

val DEBIT_CARD_PAYMENT = 6
val CREDIT_CARD_PAYMENT = 1
val BANK_TRANSACTION = 4


fun main(args: Array<String>) {

    val categories = loadCategories()

    if (false) {
        val csvFilename = "/home/andrea/Dropbox/Documents/banca/movimenti_2017.csv"
        File(OUTPUT_FILE).writeText(transformCsvData(csvFilename, categories))
        exit(0)
    }

    if (false) {
        val downloadedFile = "/home/andrea/Downloads/TXT180120125721.TAB"
        File(OUTPUT_FILE).writeText(transformBankData(File(downloadedFile).readText(Charsets.UTF_8), categories))
        exit(0)
    }

    val stringBuilder = StringBuilder()
    val dataRetriever = DataRetriever()

    // retrieves the data from the bank account
    stringBuilder.append(transformBankData(dataRetriever.getLastBankTransactions(), categories)).append("\n")

    // retrieves the data from the credit card
    stringBuilder.append(transformCreditCardData(dataRetriever.getLastCreditCardTransactions(), categories))

    // writes on disk all the last transactions
    File(OUTPUT_FILE).writeText(stringBuilder.toString())
}


fun transformBankData(bankAccountData: String, categories: Map<String, List<Triple<String, String, Int>>>) = bankAccountData
        .split("\n")
        .filter { it != "" }
        .map { toCsv(fromAbnCsv(it.split("\t"), categories, DEBIT_CARD_PAYMENT)) }
        .joinToString("\n")

fun transformCreditCardData(creditCardData: String, categories: Map<String, List<Triple<String, String, Int>>>) = creditCardData
        .split("\n")
        .filter { it != "" }
        .map { toCsv(fromAbnCsv(it.split("\t"), categories, CREDIT_CARD_PAYMENT)) }
        .joinToString("\n")

fun transformCsvData(csvFilename: String, categories: Map<String, List<Triple<String, String, Int>>>) = CSVReader(FileReader(csvFilename), ';')
        .readAll()
        .drop(1)
        .map { toCsv(fromBancaEticaCsv(it.toList(), categories, BANK_TRANSACTION)) }
        .joinToString("\n")

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
    var payment = DEBIT_CARD_PAYMENT
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


