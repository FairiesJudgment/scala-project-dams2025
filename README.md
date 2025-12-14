# Projet "Wish-list collaborative" (ex-WBocal)

## Ce que fait l'application

- Côté serveur (Scala + ZIO HTTP) :
  - Gère une liste de souhaits en mémoire (CRUD complet : création, lecture, modification, suppression).
  - Fournit un endpoint pour tirer un souhait au hasard (`/random`).
  - Diffuse en temps réel les nouveaux souhaits avec WebSocket (`/stream`).

- Côté client (fichier `Stream_site.html`) :
  - Permet d'ajouter un souhait (`title`, `details`, `priority`).
  - Affiche en temps réel les nouveaux souhaits reçus du serveur.
  - Contient une zone de test CRUD pour lister, lire, modifier ou supprimer des souhaits.

## Lancer le serveur Scala

1. Ouvrir un terminal dans le dossier racine du projet (contenant `build.sbt`).
2. Lancer la commande suivante :

```bash
sbt run
```

Le serveur démarre et écoute par défaut sur `http://localhost:8080` (fourni par `Server.default`).

## Tester l'API (exemples)

- Lister tous les wishes :

```bash
curl http://localhost:8080/wishes
```

- Créer un wish :

```bash
curl -X POST http://localhost:8080/wish -H 'Content-Type: application/json' -d '{"title":"Offrir une plante","details":"Plante verte pour le salon","priority":2}'
```

- Récupérer un wish par index :

```bash
curl http://localhost:8080/wish/0
```

- Tirer un wish aléatoire :

```bash
curl http://localhost:8080/random
```

## Lancer le client (page HTML)

1. Ouvrir `Stream_site.html` avec votre navigateur (double-clic ou `File -> Open`).
2. Vérifier que le serveur Scala est lancé sur `http://localhost:8080`.
3. Ajouter un souhait via le formulaire ; les nouveaux souhaits s'affichent en temps réel via WebSocket.

## Remarques et améliorations à venir

- Le stockage est uniquement en mémoire (`Ref[Vector[Wish]]`).
- Aucun test automatisé n'est encore présent — je peux ajouter une suite `zio-test` (unit + intégration + WebSocket) si vous le souhaitez.
- Le serveur retourne `404` pour `/random` si la liste est vide.