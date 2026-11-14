# Règles R8 minimales.
# Room, DataStore, Compose et les coroutines fournissent leurs propres règles (consumer rules) :
# le code Room est généré statiquement (DuckDatabase_Impl, DAO), aucune réflexion n'est utilisée sur
# les entités du paquet data. On ne conserve explicitement que le constructeur de la base générée,
# instanciée par Room via Class.forName.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
