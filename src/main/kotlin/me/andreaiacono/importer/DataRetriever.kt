package me.andreaiacono.importer

import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.lang.Thread.sleep
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class DataRetriever {

    private val properties: Map<String, String>
    private val driver: FirefoxDriver

    private val websiteDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")!!
    private val tabFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")!!

    private val RESOURCE_DIRECTORY = "src/main/resources/"
    private val DOWNLOAD_DIRECTORY_KEY = "downloadDirectory"

    private val GECKO_KEY = "webdriver.gecko.driver"
    private val CC_USERNAME_KEY = "cc_username"
    private val CC_PASSWORD_KEY = "cc_password"
    private val IDENTIFICATION_CODE_KEY = "identificationCode"
    private val CARD_NUMBER_KEY = "cardNumber"
    private val ACCOUNT_NUMBER_KEY = "accountNumber"

    private val PAGE_ELEMENTS_SEARCH_TIMEOUT = 30 * 1000

    init {
        properties = File(RESOURCE_DIRECTORY + "importer.properties")
                .readLines()
                .filter { it.length > 0 && !it.trimStart().startsWith("#") }
                .associate { Pair(it.split("=")[0], it.split("=")[1]) }

        if (System.getProperty("os.name").equals("Linux")) {
            System.setProperty(GECKO_KEY, properties[GECKO_KEY])
        }

        val profileDirectory = RESOURCE_DIRECTORY + "firefox_profile"
        val options = FirefoxOptions()
        options.profile = FirefoxProfile(File(profileDirectory))
        options.profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "text/plain;text/csv;application/pdf;application/octet-stream")
        options.profile.setPreference("browser.download.folderList", 2)
        options.profile.setPreference("browser.download.manager.showWhenStarting", false)
        options.profile.setPreference("browser.helperApps.alwaysAsk.force", false)
        options.profile.setPreference("browser.download.panel.shown", false)

        driver = FirefoxDriver(options)
        driver.manage().timeouts().implicitlyWait(1000, TimeUnit.SECONDS)
    }


    fun getLastCreditCardTransactions(): String {

       val cardLoginFormUrl = "https://www.icscards.nl/abnamro/login/login"
        driver.get(cardLoginFormUrl)

        // waits for the page to be loaded
        waitForElement("//input[@id='username']", driver)

        // login credentials
        val username = driver.findElementByXPath("//input[@id='username']")
        username.sendKeys(properties[CC_USERNAME_KEY])
        val password = driver.findElementByXPath("//input[@id='password']")
        password.sendKeys(properties[CC_PASSWORD_KEY])

        // login
        driver.findElementByXPath("//button-promise-loading[@type='submit']").click()

        waitForElement("//div[contains(.,'Huidige periode')]", driver)

        val sb = StringBuilder()

        // retrieves the entries
        val dates = driver.findElementsByXPath("//div[@class='i-no-line-height ng-binding']")
        val amounts = driver.findElementsByXPath("//span[@class='i-padding-left-10px i-float-right ng-binding']")
        val descriptions = driver.findElementsByXPath("//div[@class='b-transaction-header__description ng-binding ng-scope']")
        for ((index, value) in dates.withIndex()) {
            var day = value.text.substring(0..value.text.indexOf(" ")-1)
            if (day.length == 1) {
               day = "0" + day
            }
            var month = value.text.substring(value.text.indexOf(" ") + 1).dropLast(1).capitalize()
            val date = LocalDate.parse(day + " " + month + " " + LocalDateTime.now().year, websiteDateFormatter)
            var amount = amounts[index].text.substring( amounts[index].text.indexOf(" ")+1)
            amount = amount.replace(".", "")
            amount = amount.replace(",", ".")
            sb.append(listOf<String>("", "", date.format(tabFormatter), "0", "0", date.format(tabFormatter), amount, descriptions[index].text.padStart(33, ' ')).joinToString("\t")).append("\n")
        }

        driver.close()

        println(sb.toString())
        return sb.toString()
    }

    fun getLastBankTransactions(): String {

        val dataFileExtension = "TAB"
        val bankLoginFormUrl = "https://www.abnamro.nl/portalserver/en/personal/index.html?l"
        driver.get(bankLoginFormUrl)

        // waits for the pae to be loaded
        waitForElement("//input[starts-with(@id, 'wdg1')]", driver)

        // sets the five number of the identification code
        val elements = driver.findElementsByXPath("//input[starts-with(@id, 'wdg1')]")
        for (index in 4..8) {
            elements[index].sendKeys(properties[IDENTIFICATION_CODE_KEY]!![index - 4].toString())
            sleep(75)
        }

        // browses the site till the download of the data
        waitForElement("//input[@type='submit']", driver).click()
        waitForElement("//*[ text() = 'Personal account']", driver).click()
        sleep(1000)
        waitForElement("//span[ text() = 'account options']", driver).click()
        waitForElement("//li[ text() = 'Download transactions']", driver).click()
        waitForElement("//select[@id='mutationsDownloadSelectionCriteriaDTO.fileFormat']/option[text()='TXT']", driver).click()
        waitForElement("//span[ text() = 'ok']", driver).click()

        // logouts from the site
        sleep(3000)
        driver.findElementByLinkText("Log off").click()
        driver.close()

        // loads downloaded file from filesystem (the last one with the specified extension)
        val dataFile = File(properties[DOWNLOAD_DIRECTORY_KEY])
                .listFiles()
                .filter { it.absolutePath.endsWith(dataFileExtension) }
                .sortedBy { it.lastModified() }
                .firstOrNull() ?: throw Exception("No data file [*.$dataFileExtension] found in [${properties[DOWNLOAD_DIRECTORY_KEY]}")

        // returns the downloaded file
        return dataFile
                .readLines()
                .joinToString("\n")
    }

    fun waitForElement(path: String, driver: RemoteWebDriver): WebElement {

        val start = System.currentTimeMillis()

        var element: WebElement? = null
        do {
            sleep(500)
            element = try {
                driver.findElementByXPath(path)
            } catch (ex: Exception) {
                null
            }
        } while (element == null && System.currentTimeMillis() - start < PAGE_ELEMENTS_SEARCH_TIMEOUT)

        if (element == null) {
            throw Exception("Path [$path] not found in page.")
        }
        return element
    }

}



