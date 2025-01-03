# SQL Schemas

This folder contains the default schemas required in order to enable SQL based persistence.

The __main_data.sql__ schema is for basic player information and their JSON data. __skills_data.sql__ holds all skill related data. The __setup.sql__ schema can help you with setting up the initial user and database.

You will need to create your own database with an appropriate user/privileges to add the schemas. For more help on how to do this, please see [the wiki](https://github.com/luna-rs/luna/wiki/SQL-Configuration). 

# Security

Please do not use [the default password](https://github.com/luna-rs/luna/blob/master/src/main/java/io/luna/util/SqlConnectionPool.java#L38) provided to create your user. __Do not__ disable the BCrypt password encryption, this is especially important when storing player data in a database.