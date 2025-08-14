package net.bumpier.brankup.economy;

import com.edwardbelt.edprison.utils.EconomyUtils;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EdPrisonEconomyService implements IEconomyService {

    private final String currencyId;

    public EdPrisonEconomyService(String currencyId) {
        this.currencyId = currencyId;
    }

    @Override
    public CompletableFuture<Boolean> has(Player player, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();
            // Direct call to the static utility method
            double currentBalance = EconomyUtils.getEco(uuid, this.currencyId);
            return BigDecimal.valueOf(currentBalance).compareTo(amount) >= 0;
        });
    }

    @Override
    public CompletableFuture<Boolean> withdraw(Player player, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID uuid = player.getUniqueId();
                // Direct call to the static utility method
                EconomyUtils.removeEco(uuid, this.currencyId, amount.doubleValue());
                return true;
            } catch (Exception e) {
                // In case of any unexpected errors from the API
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> give(Player player, BigDecimal amount) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            // Direct call to the static utility method
            EconomyUtils.addEconomy(uuid, this.currencyId, amount.doubleValue());
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getBalance(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            double balance = EconomyUtils.getEco(player.getUniqueId(), this.currencyId);
            return BigDecimal.valueOf(balance);
        });
    }

    @Override
    public CompletableFuture<Void> set(Player player, BigDecimal amount) {
        return CompletableFuture.runAsync(() -> {
            EconomyUtils.setEco(player.getUniqueId(), this.currencyId, amount.doubleValue());
        });
    }

    @Override
    public String getCurrencyId() {
        return currencyId;
    }
}