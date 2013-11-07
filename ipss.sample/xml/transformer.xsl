<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	
	<xsl:output method="text" encoding="UTF-8"/>

	<!--
		Our goal is to come up with this query:
			SELECT film.title, inventory.total
				FROM film, inventory
				WHERE film.genre = '<favGenre>' 
					AND inventory.title = film.title 
					AND inventory.total > 0;
	-->
	
	<xsl:template match="/"> 
		<xsl:variable name="movieGenre">
			<xsl:value-of select="customer/preferences/favGenre/text()"/>
		</xsl:variable>
		
		SELECT film.title, inventory.total 
			FROM film, inventory 
			WHERE film.genre = '<xsl:value-of select="$movieGenre"/>' 
				AND film.title = inventory.title 
				AND inventory.total > 0;
	</xsl:template>

</xsl:stylesheet>


