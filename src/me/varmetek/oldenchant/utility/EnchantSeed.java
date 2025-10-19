package me.varmetek.oldenchant.utility;

import com.google.common.base.Preconditions;
import me.varmetek.oldenchant.OldEnchantPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException; // Importar InvocationTargetException
import java.util.Random;
import java.util.logging.Level; // Importar Level


/***
 *  Esta clase se utiliza para manipular la semilla de encantamiento que una mesa de encantamientos usa.
 *  Actualizado para intentar compatibilidad con versiones recientes de Minecraft usando reflexión y manejo de errores.
 * */

public class EnchantSeed
{
  private final Random random = new Random();
  private final Object container;
  // El campo de la semilla puede haber cambiado de nombre o ser privado en versiones recientes.
  // Obtenemos la referencia al campo a través de ReflectionUtil, que maneja la búsqueda y accesibilidad.
  private final Field fSeed = ReflectionUtil.getField_TableSeed();


  public EnchantSeed (Object container){
    Preconditions.checkNotNull(container,"Container cannot be null");
    // Verificar que el campo obtenido no sea nulo y sea asignable desde la clase del contenedor.
    // Esto asegura que encontramos el campo correcto para el objeto contenedor proporcionado.
    Preconditions.checkArgument(fSeed != null && fSeed.getDeclaringClass().isAssignableFrom(container.getClass()),
                               "El campo de la semilla de encantamiento (obtenido: %s) no es válido para este contenedor (%s).",
                                fSeed != null ? fSeed.getName() : "null", container.getClass().getName());
    this.container = container;
  }


  /**
   * Retorna el objeto contenedor de NMS.
   *
   * */
  public Object getContainer(){
    return container;
  }

  /**
   * Retorna la semilla actualmente usada por la mesa de encantamientos.
   * Usa reflexión para acceder al campo. Maneja excepciones de reflexión.
   * Retorna 0 en caso de error.
   * */
  public int get(){
    // Si el campo no se encontró durante la inicialización de ReflectionUtil, no podemos hacer nada.
    if (fSeed == null) {
        OldEnchantPlugin.getPluginLog().severe("El campo de la semilla de encantamiento es nulo. No se puede obtener el valor.");
        return 0; // Retornar un valor por defecto o lanzar una excepción no verificada si es apropiado
    }

    int result = 0;
    try {
      // El campo ya debería ser accesible si ReflectionUtil.getPrivateField lo hizo.
      // Obtenemos el valor entero del campo en el objeto contenedor.
      result = fSeed.getInt(container);
    } catch (IllegalAccessException e) {
      OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "No se pudo recuperar la semilla de encantamiento debido a un error de acceso:", e);
    } catch (IllegalArgumentException e) {
      OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "No se pudo recuperar la semilla de encantamiento debido a argumentos inválidos (tipo de campo incorrecto):", e);
    }
    return result;

  }
  /**
   * Permite un cambio directo a la semilla.
   * Usa reflexión para establecer el valor del campo. Maneja excepciones de reflexión.
   *
   * @param seed la nueva semilla para que la mesa de encantamientos use;
   * */

  public void set(int seed){
    // Si el campo no se encontró durante la inicialización de ReflectionUtil, no podemos hacer nada.
    if (fSeed == null) {
        OldEnchantPlugin.getPluginLog().severe("El campo de la semilla de encantamiento es nulo. No se puede establecer el valor.");
        return;
    }

    try {
      // El campo ya debería ser accesible si ReflectionUtil.getPrivateField lo hizo.
      // Establecemos el valor entero del campo en el objeto contenedor.
      fSeed.setInt(container,seed);
    } catch (IllegalAccessException e) {
      OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "No se pudo establecer la semilla de encantamiento debido a un error de acceso:", e);
    } catch (IllegalArgumentException e) {
      OldEnchantPlugin.getPluginLog().log(Level.SEVERE, "No se pudo establecer la semilla de encantamiento debido a argumentos inválidos (tipo de campo incorrecto):", e);
    }

  }


  /***
   *
   * Genera una semilla aleatoria y la establece usando el método set().
   * Retorna la semilla generada.
   *
   **/
  public int randomize(){
    int seed= random.nextInt();
    set(seed);
    return seed;
  }
}
