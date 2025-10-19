package me.varmetek.oldenchant;

import com.google.common.base.Preconditions;
import me.varmetek.oldenchant.listener.EnchantListener;
import me.varmetek.oldenchant.utility.EnchantSeed;
import me.varmetek.oldenchant.utility.ReflectionConfig;
import me.varmetek.oldenchant.utility.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field; // Importar Field
import java.lang.reflect.Method; // Importar Method
import java.util.logging.Level; // Importar Level
import java.lang.reflect.NoSuchFieldException; // Importar excepciones específicas (aunque ReflectionUtil las maneja internamente, es buena práctica saber de ellas)
import java.lang.reflect.NoSuchMethodException;
import java.lang.IllegalAccessException;


public class OldEnchantPlugin extends JavaPlugin
{

  private static Logger log; // Logger para registrar mensajes del plugin


  private ReflectionConfig reflectionConfig; // Configuración para la reflexión


 @Override
  public void onLoad() {
   log = this.getLogger();
   // Inicializar ReflectionUtil con la configuración común.
   // Este paso intenta encontrar las clases, campos y métodos internos de Minecraft usando reflexión.
   ReflectionUtil.init(Bukkit.getServer(),reflectionConfig = ReflectionConfig.COMMON);

   log.info("Running on Minecraft Version "+ ReflectionUtil.getVersion());
   // Si ReflectionUtil no se inicializó correctamente (no encontró componentes cruciales), registrar un error crítico.
   // Esto indica que el plugin probablemente no es compatible con esta versión específica del servidor.
   if (!ReflectionUtil.isInitialized()) {
       log.severe("Error grave: ReflectionUtil no pudo encontrar todos los componentes NMS/OBC necesarios. El plugin puede no ser compatible con esta versión de Minecraft.");
       // Considerar deshabilitar el plugin aquí si la reflexión falla.
       // Bukkit.getPluginManager().disablePlugin(this); // Opcional: Deshabilitar automáticamente
   } else {
       log.info("ReflectionUtil inicializado correctamente. Componentes de reflexión encontrados.");
   }

   log.severe("Para cualquier error no manejado, contacte al autor (Varmetek) en spigot.org");
  }

  @Override
  public void onDisable() {
      // Mensaje al deshabilitar el plugin
      log.info("Plugin OldEnchant deshabilitado.");
  }



  public ReflectionConfig getReflectionConfig(){
    return reflectionConfig;
  }

  @Override
  public void onEnable() {
    // Verificar si ReflectionUtil se inicializó antes de registrar el listener.
    // Si no se inicializó correctamente, el plugin no funcionará y no tiene sentido registrar eventos.
    if (ReflectionUtil.isInitialized()) {
        Bukkit.getPluginManager().registerEvents(new EnchantListener(this),this);
        log.info("Plugin OldEnchant habilitado y listener registrado.");
    } else {
        log.severe("Plugin OldEnchant no se pudo habilitar completamente debido a fallos en la reflexión durante la carga.");
        // Opcional: Deshabilitar el plugin si no se habilitó correctamente
        // Bukkit.getPluginManager().disablePlugin(this);
    }
  }



  public static Logger getPluginLog(){
    return log;
  }


  // Obtiene el objeto EntityPlayer de NMS a partir de un Player de Bukkit usando reflexión.
  // Este método es crucial para acceder a las partes internas del jugador.
  // Maneja excepciones de reflexión y retorna null si falla.
  public static  Object getEntityPlayer(Player player){
    // Asegurarse de que ReflectionUtil esté inicializado y tenga el método getHandle.
    ReflectionUtil.checkState();
    Method getHandleMethod = ReflectionUtil.getMethod_getHandle();
    if (getHandleMethod == null) {
        log.severe("Método getHandle es nulo en ReflectionUtil. No se puede obtener nmsPlayer.");
        return null;
    }

    try {
      // Invocar el método getHandle() en el objeto CraftPlayer (el player de Bukkit es una instancia de CraftPlayer)
      // para obtener el objeto EntityPlayer de NMS asociado.
      return getHandleMethod.invoke(player);
    } catch (IllegalAccessException e) {
      log.log(Level.SEVERE, "Error de acceso al intentar invocar getHandle():", e);
      return null;
    } catch (InvocationTargetException e) {
      log.log(Level.SEVERE, "Error al invocar getHandle() (posible excepción dentro del método invocado):", e);
      return null;
    } catch (Exception e) { // Capturar otras posibles excepciones inesperadas
       log.log(Level.SEVERE, "Error inesperado al obtener nmsPlayer:", e);
       return null;
    }

  }

  // Obtiene el objeto de contenedor de inventario abierto del jugador (por ejemplo, la mesa de encantamientos) usando reflexión.
  public static  Object getPlayerOpenContainer(Player player){
    // Obtener el EntityPlayer primero. Si falla, no podemos obtener el contenedor.
    Object entityPlayer = getEntityPlayer(player);
    if (entityPlayer == null) {
        log.warning("No se pudo obtener EntityPlayer para getPlayerOpenContainer. Player: " + player.getName());
        return null; // Retornar nulo si no se pudo obtener el EntityPlayer
    }

    // Llamar al método auxiliar que obtiene el contenedor a partir del EntityPlayer
    return getPlayerOpenContainer(entityPlayer);
  }

  // Método auxiliar privado para obtener el contenedor abierto a partir del objeto EntityPlayer de NMS.
  // Usa el campo openContainer/containerMenu obtenido por ReflectionUtil.
  // Maneja excepciones de reflexión y retorna null si falla.
  private  static Object getPlayerOpenContainer(Object entityPlayer){
    // Asegurarse de que ReflectionUtil esté inicializado y tenga el campo openContainer.
    ReflectionUtil.checkState();
    // Verificar que el objeto entityPlayer no sea nulo antes de intentar acceder a sus campos.
    Preconditions.checkNotNull(entityPlayer,"EntityPlayer no puede ser nulo para obtener el contenedor.");

    Field openContainerField = ReflectionUtil.getField_openContainer();
     if (openContainerField == null) {
        log.severe("Campo openContainer es nulo en ReflectionUtil. No se puede obtener el contenedor.");
        return null;
    }

    try {
      // Acceder al valor del campo openContainer (o su nombre configurado) en el objeto EntityPlayer.
      return openContainerField.get(entityPlayer);
    } catch (IllegalAccessException e) {
      log.log(Level.SEVERE, "Error de acceso al intentar obtener openContainer:", e);
      return null;
    } catch (IllegalArgumentException e) {
       log.log(Level.SEVERE, "Error de argumentos inválidos al intentar obtener openContainer (tipo de objeto incorrecto):", e);
       return null;
    } catch (Exception e) { // Capturar otras posibles excepciones inesperadas
       log.log(Level.SEVERE, "Error inesperado al obtener openContainer:", e);
       return null;
    }

  }

  // Obtiene un objeto EnchantSeed para un jugador, asumiendo que tiene una mesa de encantamientos abierta.
  // Este es el método principal llamado por EnchantListener para interactuar con la semilla.
  // Realiza verificaciones y maneja la creación del objeto EnchantSeed. Retorna null si falla.
  public static EnchantSeed getEnchantSeedInfo(Player player){
    // Asegurarse de que ReflectionUtil esté inicializado antes de proceder.
    if (!ReflectionUtil.isInitialized()) {
         log.severe("ReflectionUtil no está inicializado. No se puede obtener EnchantSeedInfo.");
         return null; // Retornar nulo si la reflexión falló en la carga del plugin
    }


    Inventory inv = player.getOpenInventory().getTopInventory();
    if (inv == null) {
         // Esto puede ocurrir si el jugador cierra el inventario justo cuando se llama este método.
         log.warning("El inventario abierto del jugador " + player.getName() + " es nulo.");
         return null;
    }
    // Verificar que el inventario abierto sea una mesa de encantamientos.
    if (inv.getType() != InventoryType.ENCHANTING) {
        // Esto es una comprobación de estado, no un error de reflexión.
        // Usar Preconditions.checkState es una forma limpia de lanzar una excepción en este caso.
         Preconditions.checkState(false,"El jugador " + player.getName() + " no está encantando (tipo de inventario: " + inv.getType() + ")."); // Esto lanzará una excepción de estado si no es ENCHANTING
         return null; // Aunque Preconditions lanza una excepción, añadimos un return null por si acaso.
    }

    // Obtener el EntityPlayer y el contenedor de inventario abierto de NMS.
    Object entityPlayer = getEntityPlayer(player);
    if (entityPlayer == null) {
        log.severe("No se pudo encontrar el handle (EntityPlayer) del jugador " + player.getName() + ".");
        return null;
    }

    Object container = getPlayerOpenContainer(entityPlayer);
     if (container == null) {
        log.severe("No se pudo encontrar el contenedor abierto del jugador " + player.getName() + ".");
        return null;
    }

    // Crear y retornar el objeto EnchantSeed con el contenedor de NMS.
    // El constructor de EnchantSeed tiene sus propias comprobaciones de Preconditions.
    try {
        return new EnchantSeed(container);
    } catch (IllegalArgumentException e) {
         log.log(Level.SEVERE, "Error al crear EnchantSeed para el jugador " + player.getName() + ": El contenedor proporcionado no es válido o el campo de semilla no coincide.", e);
         return null;
    } catch (Exception e) { // Capturar otras posibles excepciones inesperadas durante la creación de EnchantSeed
         log.log(Level.SEVERE, "Error inesperado al crear EnchantSeed para el jugador " + player.getName() + ":", e);
         return null;
    }
  }
}
