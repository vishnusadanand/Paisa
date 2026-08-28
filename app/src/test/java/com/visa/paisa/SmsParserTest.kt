package com.visa.paisa

import com.visa.paisa.parse.Categorizer
import com.visa.paisa.parse.Channel
import com.visa.paisa.parse.Direction
import com.visa.paisa.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world Indian bank alert formats. If a bank changes its wording, add the new
 * sample here first — a failing test is a much cheaper way to find out than a wrong
 * pie chart three weeks later.
 */
class SmsParserTest {

    private fun parse(body: String, sender: String = "AD-HDFCBK") = SmsParser.parse(body, sender)

    // ------------------------------------------------------------------ amounts

    @Test
    fun `reads a plain rupee amount without truncating it`() {
        val txn = parse("Spent Rs.1250.00 On HDFC Bank Credit Card xx1234 At AMAZON On 2026-08-12")
        assertNotNull(txn)
        assertEquals(1250.00, txn!!.amount, 0.001)
    }

    @Test
    fun `reads a lakh-grouped amount`() {
        val txn = parse("INR 1,25,400.50 debited from A/c XX8871 on 12-08-26 to BUILDER PAYMENTS")
        assertEquals(125400.50, txn!!.amount, 0.001)
    }

    @Test
    fun `reads an amount with no currency marker at all`() {
        val txn = parse("Dear UPI user A/C X1234 debited by 150.0 on date 12Aug26 trf to ZOMATO Refno 123456789012")
        assertEquals(150.0, txn!!.amount, 0.001)
    }

    @Test
    fun `ignores the available balance when picking the amount`() {
        val txn = parse(
            "Your A/c XX4471 is debited with Rs.340.00 on 12-08-26. Info: UPI/431203/Swiggy. Avl Bal Rs.42,310.55",
        )
        assertEquals(340.00, txn!!.amount, 0.001)
    }

    @Test
    fun `ignores the credit limit when picking the amount`() {
        val txn = parse(
            "Rs.899.00 spent on HDFC Bank Card xx9012 at NETFLIX on 03-08-26. Avl Lmt Rs.1,45,000",
        )
        assertEquals(899.00, txn!!.amount, 0.001)
    }

    // --------------------------------------------------------------- direction

    @Test
    fun `a credit card spend is a debit not a credit`() {
        val txn = parse("Spent Rs.1250.00 On HDFC Bank CREDIT Card xx1234 At CROMA On 12/08/26")
        assertEquals(Direction.DEBIT, txn!!.direction)
    }

    @Test
    fun `salary credit is read as money in`() {
        val txn = parse("Your A/c XX4471 is credited with Rs.85,000.00 on 01-08-26 by NEFT. Info: SALARY")
        assertEquals(Direction.CREDIT, txn!!.direction)
    }

    @Test
    fun `refund is read as money in`() {
        val txn = parse("Rs.499 has been refunded to your HDFC Bank Card xx1234 by MYNTRA on 09-08-26")
        assertEquals(Direction.CREDIT, txn!!.direction)
    }

    // ---------------------------------------------------------------- merchant

    @Test
    fun `pulls the payee out of a UPI path`() {
        val txn = parse("INR 450.00 debited A/c no. XX1234 12-08-26, 19:12:33 UPI/P2M/431203/Blinkit")
        assertEquals("Blinkit", txn!!.merchant)
    }

    @Test
    fun `pulls the payee out of a VPA`() {
        val txn = parse("Sent Rs.99.00 from Kotak Bank AC X1234 to swiggy@axl on 12-08-26. UPI Ref 123456789012")
        assertEquals("swiggy", txn!!.merchant)
    }

    @Test
    fun `pulls the payee after At`() {
        val txn = parse("Spent Rs.1250.00 On HDFC Bank Card xx1234 At AMAZON On 12/08/26")
        assertEquals("Amazon", txn!!.merchant)
    }

    @Test
    fun `pulls the payee after trf to and stops before the reference`() {
        val txn = parse("Dear UPI user A/C X1234 debited by 150.0 on date 12Aug26 trf to ZOMATO Refno 123456789012")
        assertEquals("Zomato", txn!!.merchant)
    }

    @Test
    fun `pulls the payee out of an Info field`() {
        val txn = parse("Acct XX123 is debited with INR 500.00 on 12-Aug-26. Info: UPI/431203/BigBasket")
        assertEquals("BigBasket", txn!!.merchant)
    }

    // ------------------------------------------------------------ non-events

    @Test
    fun `an OTP is not a transaction`() {
        assertNull(parse("123456 is your OTP for a transaction of Rs.4,999 on your HDFC Card. Do not share."))
    }

    @Test
    fun `a declined transaction is not recorded`() {
        assertNull(parse("Transaction of Rs.2,300 on your Axis Card xx1234 at FLIPKART has been declined."))
    }

    @Test
    fun `a bill reminder is not a spend`() {
        assertNull(parse("Your SBI Card statement is ready. Total amount due Rs.12,450. Due on 25-08-26."))
    }

    @Test
    fun `a balance alert with no movement is ignored`() {
        assertNull(parse("Avl Bal in your A/c XX4471 is Rs.42,310.55 as on 12-08-26."))
    }

    @Test
    fun `a cashback offer is not logged as income`() {
        assertNull(parse("Get Rs.500 cashback on your first order! Shop now at BigBazaar."))
    }

    @Test
    fun `a discount code SMS is ignored`() {
        assertNull(parse("Use code SAVE20 and get flat Rs.200 off on your next Swiggy order. T&C apply."))
    }

    @Test
    fun `a future EMI debit is a reminder not a spend`() {
        assertNull(parse("Reminder: your EMI of Rs.5,600 will be debited on 05-09-26 from A/c XX1234."))
    }

    @Test
    fun `a message with no account card or reference is not a bank alert`() {
        assertNull(parse("Rs.1000 received. Thanks for shopping with us!"))
    }

    // ------------------------------------------------------ wider bank formats

    @Test
    fun `IDFC towards-format is parsed and categorized`() {
        val body = "Your A/c XXXXXX7788 is debited by Rs.1,299.00 on 14-Aug-2026 towards SPOTIFY INDIA. Ref 556677889900 -IDFC FIRST Bank"
        val txn = parse(body, "AD-IDFCFB")!!
        assertEquals(1299.0, txn.amount, 0.001)
        assertEquals("Subscriptions", Categorizer.categorize(txn, body))
    }

    @Test
    fun `a utility VPA is categorized as a bill`() {
        val body = "Rs 300.00 debited from your A/c XX1234 on 12-08-2026 to VPA kseb@sbi. Ref 112233445566. -Federal Bank"
        val txn = parse(body, "AD-FEDBNK")!!
        assertEquals("Bills & Utilities", Categorizer.categorize(txn, body))
    }

    @Test
    fun `a wallet UPI payment is parsed`() {
        val body = "Rs.249 paid to UBER INDIA via Amazon Pay UPI. UPI Ref 778899001122"
        val txn = parse(body, "VM-AMZNPY")!!
        assertEquals(249.0, txn.amount, 0.001)
        assertEquals("Transport", Categorizer.categorize(txn, body))
    }

    @Test
    fun `a self transfer gets no invented payee name`() {
        val txn = parse("INR 25,000.00 transferred to A/c XX9988 via NEFT on 17-08-26. Ref N123456789012")!!
        assertNull(txn.merchant)
        assertEquals(Channel.NET_BANKING, txn.channel)
    }

    // ------------------------------------------------------------- categories

    @Test
    fun `swiggy is food`() {
        val body = "Sent Rs.240.00 From HDFC Bank A/C x1234 To SWIGGY On 12/08/26 Ref 123456789012"
        val txn = parse(body)!!
        assertEquals("Food & Dining", Categorizer.categorize(txn, body))
    }

    @Test
    fun `blinkit is groceries not food`() {
        val body = "INR 450.00 debited A/c no. XX1234 12-08-26 UPI/P2M/431203/Blinkit"
        val txn = parse(body)!!
        assertEquals("Groceries", Categorizer.categorize(txn, body))
    }

    @Test
    fun `amazon prime beats amazon`() {
        val body = "Rs.1499.00 spent on HDFC Card xx1234 at AMAZON PRIME on 01-08-26"
        val txn = parse(body)!!
        assertEquals("Subscriptions", Categorizer.categorize(txn, body))
    }

    @Test
    fun `plain amazon is shopping`() {
        val body = "Rs.2199.00 spent on HDFC Card xx1234 at AMAZON on 01-08-26"
        val txn = parse(body)!!
        assertEquals("Shopping", Categorizer.categorize(txn, body))
    }

    @Test
    fun `an ATM withdrawal is cash regardless of merchant text`() {
        val body = "Rs.5000 withdrawn from ATM at KOCHI on 12-08-26 from A/c XX1234"
        val txn = parse(body)!!
        assertEquals(Channel.ATM, txn.channel)
        assertEquals("Cash & ATM", Categorizer.categorize(txn, body))
    }

    @Test
    fun `an unknown local shop is left for the user to decide`() {
        val body = "Rs.320.00 debited from A/c XX1234 on 12-08-26 to SREE LAKSHMI STORES. Ref 998877665544"
        val txn = parse(body)!!
        assertEquals("Uncategorized", Categorizer.categorize(txn, body))
    }

    @Test
    fun `a user rule overrides the dictionary`() {
        val body = "Rs.320.00 debited from A/c XX1234 on 12-08-26 to SREE LAKSHMI STORES. Ref 998877665544"
        val txn = parse(body)!!
        assertEquals("Groceries", Categorizer.categorize(txn, body, userRule = "Groceries"))
    }

    // ------------------------------------------------------------- metadata

    @Test
    fun `picks up the card tail and reference number`() {
        val txn = parse("Rs.899.00 spent on HDFC Bank Card xx9012 at NETFLIX on 03-08-26. Ref 445566778899")!!
        assertEquals("9012", txn.accountTail)
        assertEquals("445566778899", txn.refNo)
    }

    @Test
    fun `identifies the bank from the message`() {
        val txn = parse("Rs.99 debited from your Federal Bank A/c XX1234 to shop@ybl on 12-08-2026")!!
        assertEquals("Federal", txn.bank)
    }

    @Test
    fun `normalizes merchant keys so overrides survive spelling noise`() {
        assertEquals(SmsParser.normalizeKey("SREE LAKSHMI STORES"), SmsParser.normalizeKey("Sree Lakshmi Stores"))
    }

    @Test
    fun `every parsed sample has a positive amount`() {
        val samples = listOf(
            "Spent Rs.1250.00 On HDFC Bank Card xx1234 At AMAZON On 12/08/26",
            "INR 450.00 debited A/c no. XX1234 12-08-26 UPI/P2M/431203/Blinkit",
            "Dear UPI user A/C X1234 debited by 150.0 on date 12Aug26 trf to ZOMATO Refno 1234567890",
        )
        samples.forEach { assertTrue(SmsParser.parse(it, "AD-BANK")!!.amount > 0) }
    }
}
