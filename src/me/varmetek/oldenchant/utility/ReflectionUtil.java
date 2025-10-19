package me.varmetek.oldenchant.utility;

import com.google.common.base.Preconditions;
import me.varmetek.oldenchant.OldEnchantPlugin; // Importar OldEnchantPlugin para usar su logger
import org.bukkit.Server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level; // Importar Level para logging

public final class ReflectionUtil
{
  private ReflectionUtil(){throw new UnsupportedOperationException("Cannot instantiate ReflectionUtil");}


  private static  String NMS_ROOT, OBC_ROOT, VERSION;



  private static Field field_tableSeed;
  private static Field field_openContainer;
  private static Method method_getHandle;
  private static Class<?> clazz_EntityPlayer;
  private static Class<?> clazz_Container;
  private static Class<?> clazz_CraftPlayer;



  public static void init(Server server,ReflectionConfig config){
    try {
        VERSION = server.getClass().getName().split("\\\\.")[3]; // Usar doble barra para escapar el punto
        NMS_ROOT = "net.minecraft.server."+VERSION+".";
        OBC_ROOT = "org.bukkit.craftbukkit."+VERSION+".";

        // Intentar obtener clases con nombres y paquetes comunes y alternativos para compatibilidad con versiones > 1.12
        clazz_CraftPlayer = getObcClass("entity.CraftPlayer");
        clazz_Container = getNmsClass("world.inventory.ContainerEnchantTable"); // Posible cambio de paquete/nombre (1.17+)
        if (clazz_Container == null) {
             clazz_Container = getNmsClass("ContainerEnchantTable"); // Nombre antiguo (<=1.16)
        }
         if (clazz_Container == null) {
             clazz_Container = getNmsClass("world.inventory.ContainerEnchantment"); // Otro posible nombre (1.14+)
        }

        clazz_EntityPlayer = getNmsClass("server.level.EntityPlayer"); // Posible cambio de paquete/nombre (1.17+)
        if (clazz_EntityPlayer == null) {
            clazz_EntityPlayer = getNmsClass("EntityPlayer"); // Nombre antiguo (<=1.16)
        }


        // Intentar obtener método getHandle() en CraftPlayer. Este método es bastante estable.
        method_getHandle = getMethod(clazz_CraftPlayer,"getHandle");


        // Intentar obtener campo activeContainer/containerMenu en EntityPlayer.
        // Se intenta con el nombre configurado y nombres comunes alternativos, usando getField (público) y getDeclaredField (privado).
        field_openContainer = getField(clazz_EntityPlayer,config.getOpenContainerName()); // Intentar campo público con nombre configurado
        if (field_openContainer == null) {
             field_openContainer = getPrivateField(clazz_EntityPlayer,config.getOpenContainerName()); // Intentar campo privado con nombre configurado
        }
        if (field_openContainer == null) {
             field_openContainer = getField(clazz_EntityPlayer, "activeContainer"); // Intentar campo público con nombre común antiguo
        }
         if (field_openContainer == null) {
             field_openContainer = getPrivateField(clazz_EntityPlayer, "activeContainer"); // Intentar campo privado con nombre común antiguo
        }
         if (field_openContainer == null) {
             // Posible nombre en versiones más recientes, ejemplo: 'containerMenu' (1.17+)
             field_openContainer = getField(clazz_EntityPlayer, "containerMenu");
         }
         if (field_openContainer == null) {
             field_openContainer = getPrivateField(clazz_EntityPlayer, "containerMenu");
         }


         // Hacer el campo accesible si se encontró (necesario para campos privados)
        if (field_openContainer != null) field_openContainer.setAccessible(true);


        // Intentar obtener campo de la semilla de la tabla de encantamientos en ContainerEnchantTable.
        // Este campo es muy propenso a cambiar de nombre y acceso. Se intentan varios nombres comunes.
        field_tableSeed =  getField(clazz_Container,config.getTableSeedName()); // Intentar campo público con nombre configurado
         if (field_tableSeed == null) {
             field_tableSeed = getPrivateField(clazz_Container,config.getTableSeedName()); // Intentar campo privado con nombre configurado
        }
         if (field_tableSeed == null) {
             field_tableSeed = getField(clazz_Container, "enchantmentSeed"); // Intentar campo público con posible nombre nuevo
        }
         if (field_tableSeed == null) {
             field_tableSeed = getPrivateField(clazz_Container, "enchantmentSeed"); // Intentar campo privado con posible nombre nuevo
        }
         if (field_tableSeed == null) {
             field_tableSeed = getField(clazz_Container, "seed"); // Intentar campo público con otro posible nombre
        }
         if (field_tableSeed == null) {
             field_tableSeed = getPrivateField(clazz_Container, "seed"); // Intentar campo privado con otro posible nombre
        }
         if (field_tableSeed == null) {
             field_tableSeed = getField(clazz_Container, "random"); // A veces el campo es el objeto Random en lugar de un int
        }
         if (field_tableSeed == null) {
             field_tableSeed = getPrivateField(clazz_Container, "random");
         }

        // Hacer el campo accesible si se encontró
        if (field_tableSeed != null) field_tableSeed.setAccessible(true);


        // Loggear si algún componente crucial no se encontró, ayudando en la depuración para versiones no soportadas
        if (clazz_CraftPlayer == null) OldEnchantPlugin.getPluginLog().severe("No se encontró la clase CraftPlayer.");
        if (clazz_Container == null) OldEnchantPlugin.getPluginLog().severe("No se encontró la clase Container (Mesa de Encantamientos). Posibles nombres: ContainerEnchantTable, ContainerEnchantment.");
        if (clazz_EntityPlayer == null) OldEnchantPlugin.getPluginLog().severe("No se encontró la clase EntityPlayer. Posibles paquetes: server.level, sin paquete.");
        if (method_getHandle == null) OldEnchantPlugin.getPluginLog().severe("No se encontró el método getHandle en CraftPlayer.");
        if (field_openContainer == null) OldEnchantPlugin.getPluginLog().severe("No se encontró el campo openContainer/containerMenu en EntityPlayer. Necesario para acceder al inventario abierto.");
        if (field_tableSeed == null) OldEnchantPlugin.getPluginLog().severe("No se encontró el campo de la semilla (o Random) en el contenedor de encantamientos. Necesario para randomizar la semilla.");


    } catch (Exception e) {
        // Capturar cualquier excepción durante la inicialización de reflexión e informarla
        OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "Error fatal durante la inicialización de ReflectionUtil:", e);
        // No deshabilitar el plugin aquí, dejar que OldEnchantPlugin maneje el fallo si isInitialized() retorna false
    }


  }

  // Verificar si todos los componentes necesarios para la reflexión se encontraron.
  // Si alguno de estos es nulo, el plugin probablemente no funcionará correctamente.
  public static boolean isInitialized(){
    return VERSION != null && NMS_ROOT !=null && OBC_ROOT != null
           && clazz_CraftPlayer != null && clazz_Container != null && clazz_EntityPlayer != null
           && method_getHandle != null && field_openContainer != null && field_tableSeed != null;

  }

  // Verificar el estado de inicialización y lanzar una excepción si no está listo.
  // Usado antes de intentar acceder a los campos/métodos obtenidos por reflexión.
  public static void checkState(){
    Preconditions.checkState(isInitialized(),"Reflection Util no está inicializado correctamente. Algunos componentes necesarios para la reflexión no se encontraron.");
  }

  // Retorna la versión del servidor detectada.
  public static String getVersion(){

    return VERSION;
  }

  // Retorna el campo Field para la semilla de la tabla de encantamientos.
  public static Field getField_TableSeed(){
    checkState(); // Verificar estado antes de retornar
    return field_tableSeed;
  }

  // Retorna el campo Field para el contenedor de inventario abierto del jugador.
  public static Field getField_openContainer(){
     checkState(); // Verificar estado antes de retornar
    return field_openContainer;
  }


  // Retorna el método Method para getHandle() en CraftPlayer.
  public static Method getMethod_getHandle(){
    checkState(); // Verificar estado antes de retornar
    return method_getHandle;
  }

  // Retorna la clase NMS EntityPlayer.
  public static Class<?> getClass_EntityPlayer(){
    return clazz_EntityPlayer;
  }

  // Retorna la clase OBC CraftPlayer.
  public static Class<?> getClass_CraftPlayer(){
    return clazz_CraftPlayer;
  }


  // Retorna la clase NMS del contenedor de encantamientos.
  public static Class<?> getClass_Container(){
    return clazz_Container;
  }


  // Métodos auxiliares para obtener clases, campos y métodos con manejo de excepciones básico.
  // Los logs de nivel FINE indican intentos que pueden fallar si se prueban nombres alternativos.
  private static Class<?> getClass(String root,String clazz){
    try {
      return Class.forName(root+clazz);
    } catch (ClassNotFoundException e) {
      // Loggear a un nivel más bajo ya que es esperado intentar nombres alternativos
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Clase no encontrada: " + root + clazz);
      return null;
    }
  }

  public static Class<?> getNmsClass(String clazz){
    return getClass(NMS_ROOT,clazz);
  }

  public static Class<?> getObcClass(String clazz){
    return getClass(OBC_ROOT,clazz);
  }


  public static  Field getField(Class<?> clazz ,String name){
    if (clazz == null) return null;
    try {
      return clazz.getField(name); // Intentar obtener campo público
    } catch (NoSuchFieldException e) {
      // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Campo público no encontrado: " + name + " en " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al campo público: " + name + " en " + clazz.getName(), e);
       return null;
    }
  }

  public static Field getPrivateField(Class<?> clazz ,String name){
     if (clazz == null) return null;
    try {
      return clazz.getDeclaredField(name); // Intentar obtener cualquier campo (público, privado, protegido)
    } catch (NoSuchFieldException e) {
      // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Campo (incluyendo privados) no encontrado: " + name + " en " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al campo (privado): " + name + " en " + clazz.getName(), e);
       return null;
    }
  }


  public static Method getMethod(Class<?> clazz ,String name){
     if (clazz == null) return null;
  try {
    return clazz.getMethod(name); // Intentar obtener método público sin parámetros
  } catch (NoSuchMethodException e) {
     // Loggear a un nivel más bajo
    OldEnchantPlugin.getPluginLog().log(Level.FINE, "Método público sin parámetros no encontrado: " + name + " en " + clazz.getName());
    return null;
  } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al método público sin parámetros: " + name + " en " + clazz.getName(), e);
       return null;
    }
}

  public static Method getMethod(Class<?> clazz ,String name, Class<?>...parameters){
     if (clazz == null) return null;
    try {
      return clazz.getMethod(name,parameters); // Intentar obtener método público con parámetros
    } catch (NoSuchMethodException e) {
       // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Método público con parámetros no encontrado: " + name + " en " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al método público con parámetros: " + name + " en " + clazz.getName(), e);
       return null;
    }
  }
  public static Method getPrivateMethod(Class<?> clazz ,String name, Class<?>...parameters){
     if (clazz == null) return null;
    try {
      return clazz.getDeclaredMethod(name,parameters); // Intentar obtener cualquier método (público, privado, protegido) con parámetros
    } catch (NoSuchMethodException e) {
       // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Método (incluyendo privados) con parámetros no encontrado: " + name + " en " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al método (privado) con parámetros: " + name + " en " + clazz.getName(), e);
       return null;
    }
  }


  public static Constructor getConstructor(Class<?> clazz, Class<?>...parameters){
     if (clazz == null) return null;
    try {
      return clazz.getConstructor(parameters); // Intentar obtener constructor público
    } catch (NoSuchMethodException e) {
       // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Constructor público no encontrado para " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al constructor público para " + clazz.getName(), e);
       return null;
    }
  }

  public static Constructor getPrivateConstructor(Class<?> clazz, Class<?>...parameters){
     if (clazz == null) return null;
    try {
      return clazz.getDeclaredConstructor(parameters); // Intentar obtener cualquier constructor (público, privado, protegido)
    } catch (NoSuchMethodException e) {
       // Loggear a un nivel más bajo
      OldEnchantPlugin.getPluginLog().log(Level.FINE, "Constructor (incluyendo privados) no encontrado para " + clazz.getName());
      return null;
    } catch (SecurityException e) {
       OldEnchantPlugin.getPluginLog().log(Level.WARNING, "Error de seguridad al intentar acceder al constructor (privado) para " + clazz.getName(), e);
       return null;
    }
  }




}
