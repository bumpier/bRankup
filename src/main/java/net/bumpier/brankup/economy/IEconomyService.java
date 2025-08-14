package net.bumpier.brankup.economy;

import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for abstracting economy operations for a specific currency.
 */
public interface IEconomyService {
    /**
     * Checks if a player has at least a certain amount of the service's currency.
     * @param player The player to check.
     * @param amount The amount to check for.
     * @return A future that completes with true if the player has enough, false otherwise.
     */
    CompletableFuture<Boolean> has(Player player, BigDecimal amount);

    /**
     * Withdraws a specific amount from a player's balance.
     * @param player The player to withdraw from.
     * @param amount The amount to withdraw.
     * @return A future that completes with true if the withdrawal was successful, false otherwise.
     */
    CompletableFuture<Boolean> withdraw(Player player, BigDecimal amount);

    /**
     * Gives a specific amount to a player's balance.
     * @param player The player to give to.
     * @param amount The amount to give.
     * @return A future that completes when the operation is done.
     */
    CompletableFuture<Void> give(Player player, BigDecimal amount);

    /**
     * Gets the player's balance for the service's currency.
     * @param player The player to check.
     * @return A future that completes with the player's balance.
     */
    CompletableFuture<BigDecimal> getBalance(Player player);

    /**
     * Sets a player's balance to a specific amount.
     * @param player The player to modify.
     * @param amount The new balance.
     * @return A future that completes when the operation is done.
     */
    CompletableFuture<Void> set(Player player, BigDecimal amount);

    /**
     * Gets the currency identifier this service instance manages.
     * @return The currency ID (e.g., "money", "tokens").
     */
    String getCurrencyId();
}