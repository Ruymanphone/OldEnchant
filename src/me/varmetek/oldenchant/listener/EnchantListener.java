package me.varmetek.oldenchant.listener;

import me.varmetek.oldenchant.OldEnchantPlugin;
import me.varmetek.oldenchant.utility.EnchantSeed; // Importar EnchantSeed
import me.varmetek.oldenchant.utility.ReflectionUtil; // Importar ReflectionUtil
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.logging.Level; // Importar Level

public class EnchantListener implements Listener
{
  private OldEnchantPlugin plugin;
  // Conjunto para rastrear jugadores cuya semilla de encantamiento ya fue reiniciada para la sesión actual.
  // Se reinicia al abrir la mesa o hacer clic/cerrar la interfaz.
  private Set<UUID> resetLock = new HashSet<>();
  // Mapa para rastrear la deuda de niveles de experiencia de los jugadores.
  // Almacena la diferencia entre el costo antiguo (30 para el tercer botón) y el costo nuevo (3).
  private Map<UUID,Integer> debt = new HashMap<>();

  public EnchantListener(OldEnchantPlugin plugin){
    this.plugin = plugin;
  }

  @EventHandler
  public void clickEvent(InventoryClickEvent ev){
    // Si el inventario clicado es nulo o no es una mesa de encantamientos, ignorar el evento.
    if(ev.getClickedInventory()== null
      || ev.getClickedInventory().getType() != InventoryType.ENCHANTING) return;

    // Eliminar al jugador del resetLock para permitir que la semilla se reinicie en la próxima preparación.
    // Hacer clic en la interfaz (incluso sin tomar un item) puede requerir un reinicio de la semilla para mantener el comportamiento antiguo.
    resetLock.remove(ev.getWhoClicked().getUniqueId());
     // OldEnchantPlugin.getPluginLog().fine("Click en mesa de encantamientos por " + ev.getWhoClicked().getName() + ". ResetLock eliminado."); // Log para depuración
  }

  @EventHandler
  public void invCloseEvent(InventoryCloseEvent ev){
    // Eliminar al jugador del resetLock al cerrar el inventario.
    resetLock.remove(ev.getPlayer().getUniqueId());
    // Limpiar la deuda de experiencia al cerrar el inventario, ya que no se aplicará si el jugador se va sin encantar.
    debt.remove(ev.getPlayer().getUniqueId());
     // OldEnchantPlugin.getPluginLog().fine("Inventario de encantamientos cerrado por " + ev.getPlayer().getName() + ". ResetLock y deuda eliminados."); // Log para depuración
  }


  @EventHandler(priority = EventPriority.MONITOR) // Usar MONITOR para asegurar que este listener se ejecuta *después* de que otros plugins (o el propio servidor) modifiquen el evento EnchantItemEvent.
  public void commitEnchant(EnchantItemEvent ev){
    Player player = ev.getEnchanter();
    // whichButton() retorna 0, 1 o 2, correspondiente a las opciones de encantamiento (generalmente niveles 1, 2, 3).
    // El costo de experiencia real del evento (ev.getExpLevelCost()) es el que Bukkit/Spigot calculó (ej. 3 para nivel 30 en versiones nuevas).

    // Queremos restaurar el costo de 30 para el encantamiento de nivel 30 (la tercera opción).
    int actualCost = ev.getExpLevelCost(); // Costo que el servidor aplicó (ej. 3 para la tercera opción en versiones nuevas)
    int intendedOldCost = -1; // Costo "antiguo" esperado. -1 como valor por defecto.

    // Determinar el costo antiguo esperado basado en el botón clicado.
    if (ev.whichButton() == 2) { // Tercera opción (generalmente nivel 30)
        intendedOldCost = 30;
    } else if (ev.whichButton() == 1) { // Segunda opción (generalmente nivel 15)
        intendedOldCost = 15; // Aunque el objetivo principal es el de nivel 30, consideramos los otros por completitud.
    } else if (ev.whichButton() == 0) { // Primera opción (generalmente nivel 1)
        intendedOldCost = 1;
    }

    // Si no se identificó un costo antiguo esperado válido, salir.
    if (intendedOldCost == -1) {
         OldEnchantPlugin.getPluginLog().warning("EnchantItemEvent con botón desconocido (" + ev.whichButton() + ") para " + player.getName());
         debt.remove(ev.getEnchanter().getUniqueId()); // Asegurarse de que no haya deuda residual
         return;
    }


    if (actualCost >= intendedOldCost) {
        // Si el costo real que aplicó el servidor ya es el costo antiguo esperado (o mayor por alguna razón), no hay deuda.
        // Esto podría ocurrir si otro plugin ya modificó el costo a 30 o más.
        debt.remove(ev.getEnchanter().getUniqueId()); // Asegurarse de que no haya deuda residual
        // OldEnchantPlugin.getPluginLog().fine("Costo real (" + actualCost + ") >= costo antiguo (" + intendedOldCost + "). No hay deuda para " + player.getName()); // Log para depuración
        return;
    }

    // Calcular la deuda de experiencia: costo antiguo esperado - costo real actual.
    int expLoss = intendedOldCost - actualCost; // Ej: para la tercera opción en 1.13+, sería 30 - 3 = 27

    // Eliminar al jugador del resetLock después de encantar. Esto permite que la semilla se resetee en la próxima interacción.
    resetLock.remove(ev.getEnchanter().getUniqueId());
    // Almacenar la deuda de experiencia para el jugador. Se aplicará en el siguiente PrepareItemEnchantEvent.
    debt.put(ev.getEnchanter().getUniqueId(),expLoss);
    // OldEnchantPlugin.getPluginLog().info("Jugador " + player.getName() + " tiene una deuda de experiencia de " + expLoss + " por encantamiento."); // Log para depuración
  }

  @EventHandler
  public void logout(PlayerQuitEvent ev){
    // Limpiar datos temporales del jugador al desconectarse para evitar memory leaks.
    resetLock.remove(ev.getPlayer().getUniqueId());
    debt.remove(ev.getPlayer().getUniqueId());
     // OldEnchantPlugin.getPluginLog().fine("Datos de EnchantListener limpiados para " + ev.getPlayer().getName() + " al desconectarse."); // Log para depuración
  }


  @EventHandler
  public void kick(PlayerKickEvent ev){
    // Limpiar datos temporales del jugador al ser expulsado para evitar memory leaks.
    resetLock.remove(ev.getPlayer().getUniqueId());
    debt.remove(ev.getPlayer().getUniqueId());
     // OldEnchantPlugin.getPluginLog().fine("Datos de EnchantListener limpiados para " + ev.getPlayer().getName() + " al ser expulsado."); // Log para depuración
  }



  @EventHandler(priority = EventPriority.HIGHEST) // Usar HIGHEST para intentar que se ejecute *antes* que otros plugins modifiquen el PrepareItemEnchantEvent.
  public void prepEnchant(PrepareItemEnchantEvent ev){
    UUID id = ev.getEnchanter().getUniqueId();
    Player player = ev.getEnchanter();

    // Si ReflectionUtil no está inicializado correctamente, no podemos modificar la semilla ni aplicar la deuda.
    if (!ReflectionUtil.isInitialized()) {
        // Ya se loggeó un error grave en onLoad si esto ocurre. Simplemente salimos.
        // OldEnchantPlugin.getPluginLog().warning("ReflectionUtil no está inicializado en PrepareItemEnchantEvent para " + player.getName()); // Log para depuración
        return;
    }

    // Si el jugador ya está en resetLock para esta sesión de preparación (es decir, ya randomizamos la semilla al abrir o interctuar), no hacer nada.
    // Esto evita randomizar la semilla múltiples veces por la misma preparación del mismo objeto.
    if(resetLock.contains(id)) {
        // OldEnchantPlugin.getPluginLog().fine("Jugador " + player.getName() + " ya está en resetLock. Saltando randomización."); // Log para depuración
        return;
    }

    // Si el jugador tiene deuda de experiencia del encantamiento anterior, aplicarla (restar niveles).
    if(debt.containsKey(id)){
      int expToLose = debt.get(id);
      // Asegurarse de que el jugador tiene suficientes niveles para perder.
      // Aunque la lógica de Bukkit debería manejar esto, una comprobación extra no está de más.
      if (player.getLevel() >= expToLose) {
          ((Player)ev.getEnchanter()).giveExpLevels(-expToLose);
          // OldEnchantPlugin.getPluginLog().info("Aplicando deuda de experiencia de " + expToLose + " niveles a " + player.getName()); // Log para depuración
      } else {
           // Esto no debería ocurrir si el costo se calculó correctamente, pero lo loggeamos por si acaso.
           OldEnchantPlugin.getPluginLog().warning("Jugador " + player.getName() + " no tiene suficientes niveles (" + player.getLevel() + ") para aplicar deuda de " + expToLose + ".");
      }
      debt.remove(id); // Eliminar la deuda después de intentar aplicarla.
    }

    // Obtener el objeto EnchantSeed para el jugador y randomizar la semilla.
    // Este es el paso clave para restaurar el comportamiento antiguo de la semilla cambiante.
    try {
        EnchantSeed enchantSeed = OldEnchantPlugin.getEnchantSeedInfo(player);
        if (enchantSeed != null) {
            int newSeed = enchantSeed.randomize();
             // OldEnchantPlugin.getPluginLog().fine("Semilla de encantamiento randomizada para " + player.getName() + " a " + newSeed); // Log para depuración
        } else {
             // El error ya se loggeó dentro de getEnchantSeedInfo, solo confirmamos que no pudimos randomizar.
             OldEnchantPlugin.getPluginLog().severe("No se pudo obtener el objeto EnchantSeed para " + player.getName() + ". La semilla no se randomizó en PrepareItemEnchantEvent.");
             // Opcional: Cancelar el evento PrepareItemEnchantEvent si la randomización falla críticamente para evitar mostrar ofertas incorrectas.
             // ev.setCancelled(true);
        }

    } catch (Exception e) {
        // Manejar posibles errores si la reflexión falla en getEnchantSeedInfo o randomize().
        OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "Error inesperado al randomizar la semilla de encantamiento para " + player.getName() + ":", e);
        // Opcional: cancelar el evento si la reflexión falla críticamente para evitar comportamientos inesperados
        // ev.setCancelled(true);
    }


    // Añadir al jugador a resetLock para evitar cambios repetidos de semilla en la misma preparación.
    resetLock.add(id);
    // OldEnchantPlugin.getPluginLog().fine("Jugador " + player.getName() + " añadido a resetLock para evitar randomización repetida."); // Log para depuración

    // Nota importante: La API de Bukkit en PrepareItemEnchantEvent permite modificar las *ofertas* de encantamiento que se muestran (ev.getOffers()).
    // Este plugin no modifica las ofertas directamente. Solo modifica la *semilla* interna que el cliente y el servidor usan para *calcular* esas ofertas.
    // Al cambiar la semilla interna, el cliente de Minecraft recalculará y mostrará nuevas ofertas de encantamiento automáticamente
    // una vez que el evento PrepareItemEnchantEvent termine y el servidor envíe el paquete de actualización al cliente.
    // Por lo tanto, no es necesario modificar ev.getOffers() en este caso para lograr el comportamiento deseado de la 1.7.
  }
}
