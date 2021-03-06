package name.abuchen.portfolio.datatransfer.pdf;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Money;

public class BaaderBankPDFExtractor extends AbstractPDFExtractor
{

    public BaaderBankPDFExtractor(Client client)
    {
        super(client);

        // Scalable Capital would actually be the preferred Bank Identifier. However, the bill sent from Scalable Capital is not correctly
        // read with PDFBox and the sort-option set to true. Therefore, we currently have to rely on the Baader Bank identifier.
        addBankIdentifier("Baader Bank"); //$NON-NLS-1$
        addBankIdentifier("Scalable Capital"); //$NON-NLS-1$
        addBankIdentifier("GRATISBROKER GmbH"); //$NON-NLS-1$

        addBuyTransaction();
        addSellTransaction();
        addDividendTransaction();
        addTaxAdjustmentTransaction();
        addFeesAssetManagerTransaction();
        addPeriodenauszugTransactions();

    }

    @Override
    public String getPDFAuthor()
    {
        return "Scalable Capital Vermögensverwaltung GmbH"; //$NON-NLS-1$
    }

    @SuppressWarnings("nls")
    private void addBuyTransaction()
    {
        DocumentType type = new DocumentType("Wertpapierabrechnung: Kauf");
        this.addDocumentTyp(type);

        Block block = new Block(".* Portfolio: .*");
        type.addBlock(block);
        block.set(new Transaction<BuySellEntry>().subject(() -> {
            BuySellEntry entry = new BuySellEntry();
            entry.setType(PortfolioTransaction.Type.BUY);
            return entry;
        })

                        .section("isin", "wkn", "name")
                        .match("Nominale *ISIN: *(?<isin>[^ ]*) *WKN: *(?<wkn>[^ ]*) *Kurs *")
                        .match("STK [^ ]* (?<name>.*) (?<currency>\\w{3}) [\\d.,]+,\\d{2,}+")
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        .section("shares")
                        .match("STK *(?<shares>[\\.\\d]+[,\\d]*) .*")
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        .oneOf(

                                        section -> section.attributes("date", "time")
                                                        .find("Handelsdatum *Handelsuhrzeit")
                                                        .match("^(?<date>\\d+\\.\\d+\\.\\d{4}) (?<time>\\d+:\\d+).*$")
                                                        .assign((t, v) -> t
                                                                        .setDate(asDate(v.get("date"), v.get("time")))),

                                        section -> section.attributes("date", "time")
                                                        .find("Nominale Kurs Ausführungsplatz datum uhrzeit")
                                                        .match("^STK .* (?<date>\\d+\\.\\d+\\.\\d{4}) (?<time>\\d+:\\d+).*")
                                                        .assign((t, v) -> t
                                                                        .setDate(asDate(v.get("date"), v.get("time"))))

                        )

                        .section("amount", "currency")
                        .match("Zu Lasten Konto \\d+ Valuta: \\d+\\.\\d+\\.\\d{4} *(?<currency>\\w{3}) *(?<amount>[\\d.]+,\\d{2})")
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .section("fee", "currency").optional()
                        .match("Provision *(?<currency>\\w{3}) *(?<fee>[\\d.]+,\\d{2})")
                        .assign((t, v) -> t.getPortfolioTransaction().addUnit(new Unit(Unit.Type.FEE,
                                          Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("fee"))))))

                        .wrap(BuySellEntryItem::new));
    }

    @SuppressWarnings("nls")
    private void addSellTransaction()
    {
        DocumentType type = new DocumentType("Wertpapierabrechnung: Verkauf.*");
        this.addDocumentTyp(type);

        Block block = new Block("Auftragsdatum.*");
        type.addBlock(block);
        block.set(new Transaction<BuySellEntry>().subject(() -> {
            BuySellEntry entry = new BuySellEntry();
            entry.setType(PortfolioTransaction.Type.SELL);
            return entry;
        })

                        .section("name", "isin", "wkn", "currency")
                        .match("Nominale *ISIN: *(?<isin>[^ ]*) *WKN: *(?<wkn>[^ ]*) .*")
                        .match("STK [^ ]+ +(?<name>.*) (?<currency>\\w{3}) [\\d.,]+")
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        .section("shares")
                        .match("STK *(?<shares>[\\.\\d]+[,\\d]*) .*")
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        .oneOf(

                                        section -> section.attributes("date", "time")
                                                        .find("Handelsdatum *Handelsuhrzeit")
                                                        .match("^(?<date>\\d+\\.\\d+\\.\\d{4}) (?<time>\\d+:\\d+).*$")
                                                        .assign((t, v) -> t
                                                                        .setDate(asDate(v.get("date"), v.get("time")))),

                                        section -> section.attributes("date", "time")
                                                        .find("Nominale Kurs Ausführungsplatz datum uhrzeit")
                                                        .match("^STK .* (?<date>\\d+\\.\\d+\\.\\d{4}) (?<time>\\d+:\\d+).*")
                                                        .assign((t, v) -> t
                                                                        .setDate(asDate(v.get("date"), v.get("time"))))

                        )

                        .section("amount", "currency")
                        .match("Zu Gunsten Konto \\d+ Valuta: \\d+\\.\\d+\\.\\d{4} *(?<currency>\\w{3}) *(?<amount>[\\d.]+,\\d{2})")
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .section("tax", "currency").optional()
                        .match("Kapitalertragsteuer (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.getPortfolioTransaction().addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("tax", "currency").optional()
                        .match("Kirchensteuer (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.getPortfolioTransaction().addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("tax", "currency").optional()
                        .match("Solidaritätszuschlag (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.getPortfolioTransaction().addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("fee", "currency").optional()
                        .match("Provision (?<currency>\\w{3}) (?<fee>[\\d.]+,\\d{2}) -") //
                        .assign((t, v) -> t.getPortfolioTransaction().addUnit(new Unit(Unit.Type.FEE,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("fee"))))))

                        .wrap(t -> new BuySellEntryItem(t)));
    }

    @SuppressWarnings("nls")
    private void addDividendTransaction()
    {
        DocumentType type1 = new DocumentType("Fondsausschüttung");
        DocumentType type2 = new DocumentType("Ertragsthesaurierung");
        DocumentType type3 = new DocumentType("Dividendenabrechnung");

        this.addDocumentTyp(type1);
        this.addDocumentTyp(type2);
        this.addDocumentTyp(type3);

        Block block = new Block("Ex-Tag.*");
        type1.addBlock(block);
        type2.addBlock(block);
        type3.addBlock(block);
        block.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.DIVIDENDS);
            return t;
        })

                        .section("isin", "wkn")
                        .match("Nominale *ISIN: *(?<isin>[^ ]*) *WKN: *(?<wkn>[^ ]*) .*")
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        .section("shares")
                        .match("STK *(?<shares>[\\.\\d]+[,\\d]*) .*")
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        .section("date", "amount", "currency")
                        .match("Zu Gunsten Konto \\d+ Valuta: (?<date>\\d+.\\d+.\\d{4}) *(?<currency>\\w{3}) *(?<amount>[\\d.]+,\\d{2})")
                        .assign((t, v) -> {
                            t.setDateTime(asDate(v.get("date")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .section("tax", "currency").optional()
                        .match("Kapitalertragsteuer (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("tax", "currency").optional()
                        .match("Kirchensteuer (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("tax", "currency").optional()
                        .match("Solidaritätszuschlag (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .section("tax", "currency").optional()
                        .match("(US-Quellensteuer|Quellensteuer) (?<currency>\\w{3}) (?<tax>[\\d.]+,\\d{2}) -")
                        .assign((t, v) -> t.addUnit(new Unit(Unit.Type.TAX,
                                        Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax"))))))

                        .wrap(t -> new TransactionItem(t)));
    }

    @SuppressWarnings("nls")
    private void addTaxAdjustmentTransaction()
    {

        DocumentType type = new DocumentType("Steuerausgleichsrechnung");
        this.addDocumentTyp(type);

        Block block = new Block("Seite .*");
        type.addBlock(block);
        block.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.TAX_REFUND);
            return t;
        })

                        .oneOf(

                                        section -> section.attributes("date")
                                                        .match("Unterschleißheim, (?<date>\\d+.\\d+.\\d{4})")
                                                        .assign((t, v) -> t.setDateTime(asDate(v.get("date"))))

                                        ,
                                        section -> section.attributes("date")
                                                        .match("^(?<date>\\d+.\\d+.\\d{4})")
                                                        .assign((t, v) -> t.setDateTime(asDate(v.get(
                                                                        "date")))))
                      
                        .section("amount", "currency")
                        .match("Erstattung *(?<currency>\\w{3}) *(?<amount>[\\d.]+,\\d{2})")
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .wrap(t -> new TransactionItem(t)));
    }

    @SuppressWarnings("nls")
    private void addFeesAssetManagerTransaction()
    {

        DocumentType type = new DocumentType("Vergütung des Vermögensverwalters");
        this.addDocumentTyp(type);

        Block block = new Block("Rechnung für .*");
        type.addBlock(block);
        block.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.FEES);
            return t;
        })

                        .section("currency", "amount")
                        .match("Leistungen Beträge \\((?<currency>\\w{3})\\).*")
                        .match("Rechnungsbetrag *(?<amount>[\\d.]+,\\d{2}).*")
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .section("date")
                        .match("Abbuchungsdatum: (?<date>\\d+.\\d+.\\d{4})")
                        .assign((t, v) -> t.setDateTime(asDate(v.get("date"))))

                        .wrap(t -> new TransactionItem(t)));
    }

    @SuppressWarnings("nls")
    private void addPeriodenauszugTransactions()
    {
        final DocumentType type = new DocumentType("Perioden-Kontoauszug", (context, lines) -> {
            Pattern pCurrency = Pattern.compile("Perioden-Kontoauszug:[ ]+(\\w{3}+)-Konto");
            // read the current context here
            for (String line : lines)
            {
                Matcher m = pCurrency.matcher(line);
                if (m.matches())
                {
                    context.put("currency", m.group(1));
                }
            }
        });
        this.addDocumentTyp(type);

        // deposit, add value to account
        // 01.01.2020 Lastschrift aktiv 01.01.2020 123,45
        Block depositBlock = new Block(
                        "(\\d+\\.\\d+\\.\\d{4}) (Lastschrift aktiv) (\\d+\\.\\d+\\.\\d{4}) ([\\d.]+,\\d{2})");
        type.addBlock(depositBlock);
        depositBlock.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.DEPOSIT);
            return t;
        })

                        .section("valuta", "amount")
                        .match("(\\d+\\.\\d+\\.\\d{4}) (Lastschrift aktiv) (?<valuta>\\d+\\.\\d+\\.\\d{4}) (?<amount>[\\d.]+,\\d{2})")
                        .assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setDateTime(asDate(v.get("valuta")));
                            t.setAmount(asAmount(v.get("amount")));
                            // t.setNote(v.get("text"));
                        }).wrap(t -> new TransactionItem(t)));


        
        
        Block removalBlock = new Block(
                        "(\\d+\\.\\d+\\.\\d{4}) (SEPA-Ueberweisung) (\\d+\\.\\d+\\.\\d{4}) ([\\d.]+,\\d{2}) (-)");
        type.addBlock(removalBlock);
        removalBlock.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.REMOVAL);
            return t;
        })

                        .section("valuta", "amount")
                        .match("(\\d+\\.\\d+\\.\\d{4}) (SEPA-Ueberweisung) (?<valuta>\\d+\\.\\d+\\.\\d{4}) (?<amount>[\\d.]+,\\d{2}) (-)")
                        .assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setDateTime(asDate(v.get("valuta")));
                            t.setAmount(asAmount(v.get("amount")));
                        }).wrap(t -> new TransactionItem(t)));

        Block feesBlock = new Block(
                        "(\\d+\\.\\d+\\.\\d{4}) (Lastschrift aktiv|Transaktionskostenpauschale o. MwSt.) (?<valuta>\\d+\\.\\d+\\.\\d{4}) (?<amount>[\\d.]+,\\d{2}) (-)");
        type.addBlock(feesBlock);
        feesBlock.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.FEES);
            return t;
        })

                        .section("valuta", "amount")
                        .match("(\\d+\\.\\d+\\.\\d{4}) (Lastschrift aktiv|Transaktionskostenpauschale o. MwSt.) (?<valuta>\\d+\\.\\d+\\.\\d{4}) (?<amount>[\\d.]+,\\d{2}) (-)")
                        .assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setDateTime(asDate(v.get("valuta")));
                            t.setAmount(asAmount(v.get("amount")));
                        }).wrap(t -> new TransactionItem(t)));

        Block buyBlock = new Block("(\\d+.\\d+.\\d{4}+) (Kauf) (\\d+.\\d+.\\d{4}+).*");
        type.addBlock(buyBlock);
        buyBlock.set(new Transaction<BuySellEntry>().subject(() -> {
            BuySellEntry entry = new BuySellEntry();
            entry.setType(PortfolioTransaction.Type.BUY);
            return entry;
        })

                        .section("isin", "name").match("(\\d+.\\d+.\\d{4}+) (Kauf) (\\d+.\\d+.\\d{4}+).*")
                        .match("^(?<name>.*)$").match("^ISIN (?<isin>.{12})").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            v.put("currency", context.get("currency"));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .section("amount", "date", "shares")
                        .match("(\\d+.\\d+.\\d{4}+) (Kauf) (?<date>\\d+.\\d+.\\d{4}+) (?<amount>[\\d.,]+).*")
                        .match("^(.*)$").match("^ISIN .{12}").match("^STK *(?<shares>[\\d.,]+).*").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setDate(asDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        }).wrap(BuySellEntryItem::new));

        Block sellBlock = new Block("(\\d+.\\d+.\\d{4}+) (Verkauf) (\\d+.\\d+.\\d{4}+).*");
        type.addBlock(sellBlock);
        sellBlock.set(new Transaction<BuySellEntry>().subject(() -> {
            BuySellEntry entry = new BuySellEntry();
            entry.setType(PortfolioTransaction.Type.SELL);
            return entry;
        })

                        .section("isin", "name").match("(\\d+.\\d+.\\d{4}+) (Verkauf) (\\d+.\\d+.\\d{4}+).*")
                        .match("^(?<name>.*)$").match("^ISIN (?<isin>.{12})").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            v.put("currency", context.get("currency"));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .section("amount", "date", "shares")
                        .match("(\\d+.\\d+.\\d{4}+) (Verkauf) (?<date>\\d+.\\d+.\\d{4}+) (?<amount>[\\d.,]+).*")
                        .match("^(.*)$").match("^ISIN .{12}").match("^STK *(?<shares>[\\d.,]+).*").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setDate(asDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        }).wrap(BuySellEntryItem::new));

        Block dividendBlock = new Block("(\\d+.\\d+.\\d{4}+) (Coupons/Dividende) (\\d+.\\d+.\\d{4}+).*");
        type.addBlock(dividendBlock);
        dividendBlock.set(new Transaction<AccountTransaction>().subject(() -> {
            AccountTransaction t = new AccountTransaction();
            t.setType(AccountTransaction.Type.DIVIDENDS);
            return t;
        })

                        .section("isin", "name").match("(\\d+.\\d+.\\d{4}+) (Coupons/Dividende) (\\d+.\\d+.\\d{4}+).*")
                        .match("^(?<name>.*)$").match("^(ISIN )*(?<isin>.{12})").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            v.put("currency", context.get("currency"));
                            t.setSecurity(getOrCreateSecurity(v));
                        })

                        .section("amount", "date", "shares")
                        .match("(\\d+.\\d+.\\d{4}+) (Coupons/Dividende) (?<date>\\d+.\\d+.\\d{4}+) (?<amount>[\\d.,]+).*")
                        .match("^(.*)$").match("^(.*)$").match("^STK *(?<shares>[\\d.,]+).*").assign((t, v) -> {
                            Map<String, String> context = type.getCurrentContext();
                            t.setDateTime(asDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setCurrencyCode(asCurrencyCode(context.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        }).wrap(t -> new TransactionItem(t)));
    }

    @Override
    public String getLabel()
    {
        return "Baader Bank / Scalable Capital"; //$NON-NLS-1$
    }
}
