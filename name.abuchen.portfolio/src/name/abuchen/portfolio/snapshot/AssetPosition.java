package name.abuchen.portfolio.snapshot;

import java.util.Comparator;
import java.util.Date;

import name.abuchen.portfolio.model.InvestmentVehicle;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;

public class AssetPosition
{
    public static final class ByDescription implements Comparator<AssetPosition>
    {
        @Override
        public int compare(AssetPosition p1, AssetPosition p2)
        {
            return p1.getDescription().compareTo(p2.getDescription());
        }
    }

    private final SecurityPosition position;
    private final CurrencyConverter converter;
    private final Date date;
    private final Money totalAssets;
    private final Money valuation;

    /* package */AssetPosition(SecurityPosition position, CurrencyConverter converter, Date date, Money totalAssets)
    {
        this.position = position;
        this.converter = converter;
        this.date = date;
        this.totalAssets = totalAssets;
        this.valuation = position.calculateValue();
    }

    public Money getValuation()
    {
        return converter.convert(date, valuation);
    }

    public double getShare()
    {
        return (double) getValuation().getAmount() / (double) this.totalAssets.getAmount();
    }

    public Money getFIFOPurchaseValue()
    {
        return converter.convert(date, position.getFIFOPurchaseValue());
    }

    public Money getProfitLoss()
    {
        return converter.convert(date, position.getProfitLoss());
    }

    public String getDescription()
    {
        InvestmentVehicle investmentVehicle = position.getInvestmentVehicle();
        return investmentVehicle != null ? investmentVehicle.getName() : position.getSecurity().getName();
    }

    public Security getSecurity()
    {
        return position.getSecurity();
    }

    public SecurityPosition getPosition()
    {
        return position;
    }

    public InvestmentVehicle getInvestmentVehicle()
    {
        return position.getInvestmentVehicle();
    }
}