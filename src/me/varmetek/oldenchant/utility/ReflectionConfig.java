package me.varmetek.oldenchant.utility;

import java.util.logging.Logger;


/****
 *
 * Para lidiar con incompatibilidades entre diferentes versiones de Minecraft,
 * este enum contiene diferentes opciones que especifican los nombres
 * de campos internos de NMS/OBC que pueden cambiar entre versiones.
 * Actualizado para documentar posibles nombres de campo en versiones recientes,
 * aunque ReflectionUtil ahora maneja gran parte de esta variación internamente.
 * */
public enum ReflectionConfig
{

  /***
   * Esta configuración contiene nombres de campo comunes usados en múltiples versiones antiguas.
   * 'activeContainer' es común para el contenedor abierto del jugador (hasta 1.16).
   * 'f' fue un nombre de campo común para la semilla de encantamiento en ContainerEnchantTable en versiones antiguas (hasta 1.12).
   *
   * ReflectionUtil ahora intentará varios nombres alternativos si estos fallan,
   * por lo que esta configuración COMMON es más una referencia histórica o un punto de partida.
   * */
  COMMON("activeContainer","f");


  private final Logger log = Logger.getLogger("Minecraft"); // Logger, aunque no se usa directamente en este enum.


  private  final String openContainerName, tableSeedName; // Nombres de los campos configurados.

  // Constructor del enum
  ReflectionConfig (String activeContainer, String tableSeed){
   this.openContainerName = activeContainer;
   this.tableSeedName = tableSeed;
  }



  /***
   * retorna el nombre del campo configurado para acceder al inventario activo del jugador (contenedor).
   * ReflectionUtil intentará nombres alternativos si este falla al buscar.
   * **/
  public String getOpenContainerName(){
    return openContainerName;
  }



  /***
   * retorna el nombre del campo configurado para acceder a la semilla de encantamiento de la mesa de encantamientos.
   * ReflectionUtil intentará nombres alternativos si este falla al buscar.
   * **/
  public String getTableSeedName(){
    return tableSeedName;
  }
}
