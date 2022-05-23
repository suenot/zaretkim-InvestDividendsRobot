package org.zaretkim.dividendsrobot.test;

import com.google.protobuf.Timestamp;
import org.zaretkim.dividendsrobot.service.MarketService;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.utils.MapperUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Mocking MarketService for unit tests
 */
public class TestMarketService implements MarketService {
    public static final int LOT = 10;
    private final HashMap<String, PortfolioPosition> portfolioPositions = new HashMap<>();
    private BigDecimal cash = BigDecimal.valueOf(100000);
    private final HashMap<String, Integer> expectedSells = new HashMap<>();
    private final List<String> unexpectedSells = new ArrayList<>();
    private final HashMap<String, Integer> expectedBuys = new HashMap<>();
    private final List<String> unexpectedBuys = new ArrayList<>();
    private boolean isWorkingHours = true;
    private final Instant fakeNow = Instant.now();
    private final HashMap<String, List<Dividend>> dividendMap = new HashMap<>();
    private final HashMap<String, LastPrice> pricesMap = new HashMap<>();

    @Override
    public PortfolioResponse getPortfolio() {
        BigDecimal totalSharesCount = BigDecimal.ZERO;
        for (PortfolioPosition position: portfolioPositions.values()) {
            var figi = position.getFigi();
            var price = getLastPricesSync(figi);
            var quantity = position.getQuantity();
            var amount = MapperUtils.quotationToBigDecimal(price.getPrice()).multiply(BigDecimal.valueOf(quantity.getUnits()));
            totalSharesCount = totalSharesCount.add(amount);
        }
        return PortfolioResponse.newBuilder().
                addAllPositions(portfolioPositions.values()).
                setTotalAmountShares(MapperUtils.bigDecimalToMoneyValue(totalSharesCount)).
                setTotalAmountCurrencies(MapperUtils.bigDecimalToMoneyValue(cash)).
                build();
    }

    @Override
    public boolean isWorkingHours() {
        return isWorkingHours;
    }

    public void setWorkingHours(boolean isWorkingHours) {
        this.isWorkingHours = isWorkingHours;
    }

    @Override
    public Share getShareByFigiSync(String figi) {
        return Share.newBuilder().setFigi(figi).setLot(LOT).build();
    }

    @Override
    public List<Dividend> getDividendsSync(String figi) {
        List<Dividend> dividends = dividendMap.get(figi);
        if (dividends == null)
            return Collections.emptyList();
        return dividends;
    }

    public void addDividend(String figi, Instant lastBuyDate, double dividendNet) {
        var dividendNetValue = MapperUtils.bigDecimalToMoneyValue(BigDecimal.valueOf(dividendNet));
        var lastBuyDateTimeStamp = Timestamp.newBuilder().setSeconds(lastBuyDate.getEpochSecond()).build();
        var dividend = Dividend.newBuilder().
                setLastBuyDate(lastBuyDateTimeStamp).
                setDividendNet(dividendNetValue).
                build();
        var dividends = dividendMap.computeIfAbsent(figi, k -> new ArrayList<>());
        dividends.add(dividend);
    }

    @Override
    public LastPrice getLastPricesSync(String figi) {
        return pricesMap.get(figi);
    }

    public void setLastPrice(String figi, double price) {
        var lastPrice = LastPrice.newBuilder().
                setFigi(figi).
                setPrice(MapperUtils.bigDecimalToQuotation(BigDecimal.valueOf(price))).
                build();
        pricesMap.put(figi, lastPrice);
    }

    @Override
    public Instant now() {
        return fakeNow;
    }

    @Override
    public String sellMarket(String figi, int numberOfLots) {
        Integer expectedNumber = expectedSells.remove(figi);
        if (expectedNumber == null || expectedNumber != numberOfLots) {
            unexpectedSells.add(figi);
        }
        return null;
    }

    @Override
    public String buyMarket(String figi, int numberOfLots) {
        Integer expectedNumber = expectedBuys.remove(figi);
        if (expectedNumber == null || expectedNumber != numberOfLots)
            unexpectedBuys.add(figi);
        return null;
    }

    public void assertAllSellsAndBuysAreDone() {
        if (expectedBuys.size() > 0 || expectedSells.size() > 0)
            throw new AssertionError("Not all expected operations are done");
        if (unexpectedSells.size() > 0)
            throw new AssertionError("Unexpected sells for figies: " + listToString(unexpectedSells, ", "));
        if (unexpectedBuys.size() > 0)
            throw new AssertionError("Unexpected sells for figies: " + listToString(unexpectedBuys, ", "));
    }

    private String listToString(List<?> list, String separator) {
        var sb = new StringBuilder();
        var first = true;
        for (var o: list) {
            if (!first)
                sb.append(separator);
            sb.append(o.toString());
            first = false;
        }
        return sb.toString();
    }

    @Override
    public List<OrderState> getOrders() {
        return Collections.emptyList();
    }

    @Override
    public void cancelOrder(String orderId) {

    }

    @Override
    public String validateToken() {
        return null;
    }

    public void setCash(long cash) {
        this.cash = BigDecimal.valueOf(cash);
    }

    public void expectedSell(String figi, int count) {
        expectedSells.put(figi, count);
    }

    public void expectedBuy(String figi, int count) {
        expectedBuys.put(figi, count);
    }

    public void addPosition(String figi, int numberOfLots, double averagePrice, double currentPrice) {
        var portfolioPosition = PortfolioPosition.newBuilder().setFigi(figi).
                setQuantityLots(MapperUtils.bigDecimalToQuotation(BigDecimal.valueOf(numberOfLots))).
                setAveragePositionPrice(moneyValueFromDouble(averagePrice)).
                setCurrentPrice(moneyValueFromDouble(currentPrice)).
                setInstrumentType("share").
                build();
        portfolioPositions.put(figi, portfolioPosition);
    }

    private MoneyValue moneyValueFromDouble(double value) {
        var v1 = MapperUtils.bigDecimalToMoneyValue(BigDecimal.valueOf(value));
        return MoneyValue.newBuilder(v1).setCurrency("RUB").build();
    }
}
