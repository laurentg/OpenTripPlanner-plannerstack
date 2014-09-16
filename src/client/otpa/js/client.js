/* This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public License
   as published by the Free Software Foundation, either version 3 of
   the License, or (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

/*
 * OpenTripPlanner Analyst JavaScript library demo (Marseille Metropole).
 */
$(function() {

    /* Global context object. */
    var gui = {};
    /* Our reference point for diff mode */
    gui.GRID_ORIGIN = L.latLng(43.6, 1.4)

    /* Initialize a leaflet map */
    gui.map = L.map('map', {
        minZoom : 10,
        maxZoom : 18,
    }).setView(L.latLng(43.604, 1.443), 12);

    /* Add OSM/OpenTransport layers. TODO Add MapBox layer. */
    gui.osmLayer = new L.TileLayer("http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png", {
        subdomains : [ "otile1", "otile2", "otile3", "otile4" ],
        maxZoom : 18,
        attribution : "Map data &copy; OpenStreetMap contributors"
    });
    gui.otLayer = new L.TileLayer(
            "http://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png",
            {
                subdomains : [ "a", "b", "c" ],
                maxZoom : 18,
                attribution : "Map data &copy; OpenStreetMap contributors & <a href='http://www.thunderforest.com/'>Thunderforest</a>"
            });
    gui.map.addLayer(gui.otLayer);

    /* Create 3 layer groups for easier display / management */
    gui.gradientLayerGroup = new L.LayerGroup([]);
    gui.isochronesLayerGroup = new L.LayerGroup([]);
    gui.map.addLayer(gui.gradientLayerGroup);
    gui.map.addLayer(gui.isochronesLayerGroup);

    /* Add controls to the map */
    gui.layerControl = L.control.layers({
        "Transport" : gui.otLayer,
        "OSM" : gui.osmLayer
    }, {
        "Gradient" : gui.gradientLayerGroup,
        "Isochrones" : gui.isochronesLayerGroup,
    });
    gui.layerControl.addTo(gui.map);

    /* Load populations and add markers */
    function addPopulationMarkers(population, pathOptions) {
    }

    gui.populations = [];
    function addPopulation(layerName, filename, nameColName, key, color) {
        var pop = new otp.analyst.Population();
        pop.loadFromCsv(filename, {
            lonColName : "X_WGS84",
            latColName : "Y_WGS84",
            nameColName : nameColName
        }, ";").onLoad(
                function(population) {
                    var pathOptions = {
                        radius : 4,
                        color : "#000",
                        opacity : 0.8,
                        fillOpacity : 0.8,
                        fillColor : color
                    };
                    population.layer = new L.LayerGroup([]);
                    gui.layerControl.addOverlay(population.layer, layerName);
                    for (var i = 0; i < population.size(); i++) {
                        var item = population.get(i);
                        population.layer.addLayer(L.circleMarker(item.location, pathOptions).bindPopup(
                                layerName + ":" + item.name));
                    }
                });
        pop.key = key;
        gui.populations.push(pop);
    }
    addPopulation("Crèches", "Creches.csv", "NOM", "cr", "#FC0");
    addPopulation("Écoles maternelles", "Ecoles_Mat_Publiques.csv", "Ecole", "em", "#F80");
    addPopulation("Écoles élémentaires", "Ecoles_Elem_Publiques.csv", "Ecole", "ee", "#F00");
    addPopulation("Bibliothèques", "Bibliotheques.csv", "Nom", "bi", "#0C0");
    addPopulation("Cinémas", "Cinema.csv", "EQ_NOM_EQUIPEMENT", "ci", "#0CC");
    addPopulation("Équipements Culturels", "Equipement_culturel.csv", "NOM", "ec", "#08C");
    addPopulation("Piscines", "Piscines.csv", "nom_complet", "pi", "#00C");

    /* Select client-wide locale */
    otp.setLocale(otp.locale.French);

    /* Create a request parameter widget */
    gui.widget1 = new otp.analyst.ParamsWidget($('#widget1'), {
        coordinateOrigin : gui.GRID_ORIGIN,
        selectMaxTime : true,
        map : gui.map,
        defaultRouterId : 'toulouse'
    });

    /* Called whenever some parameters have changed. */
    function refresh() {
        /* Disable the refresh button to prevent too many calls */
        $("#refresh").prop("disabled", true);
        /* Get the current parameter values */
        var params1 = gui.widget1.getParameters();
        var max = params1.zDataType == "BOARDINGS" ? 5
                : params1.zDataType == "WALK_DISTANCE" ? params1.maxWalkDistance * 1.2 : params1.maxTimeSec;
        /* Get a TimeGrid from the server. */
        gui.timeGrid = new otp.analyst.TimeGrid(params1).onLoad(function(timeGrid) {
            /* Create a ColorMap */
            gui.colorMap = new otp.analyst.ColorMap({
                max : max,
                zDataType : params1.zDataType
            });
            gui.colorMap.setLegendCanvas($("#legend").get(0));
            /* Clear old layers, add a new one. */
            gui.gradientLayerGroup.clearLayers();
            gui.layer = otp.analyst.TimeGrid.getLeafletLayer(gui.timeGrid, gui.colorMap);
            gui.layer.setOpacity(0.5);
            gui.gradientLayerGroup.addLayer(gui.layer);
            gui.layer.bringToFront(); // TODO Leaflet bug?
            /* Re-enable refresh button */
            $("#refresh").prop("disabled", false);

            /* Update scores, cutoff depends on data we have available. */
            var cutoff1 = params1.zDataType == "BOARDINGS" ? 1.5 : params1.zDataType == "WALK_DISTANCE" ? 400 : 1800;
            var cutoff2 = params1.zDataType == "BOARDINGS" ? 2.5 : params1.zDataType == "WALK_DISTANCE" ? 800 : 3600;
            var labels = params1.zDataType == "BOARDINGS" ? [ "<1 c.", "<2 c." ]
                    : params1.zDataType == "WALK_DISTANCE" ? [ "<400m", "<800m" ] : [ "<30mn", "<1h" ];
            var scorer = new otp.analyst.Scoring();
            var edge1 = otp.analyst.Scoring.stepEdge(cutoff1);
            var edge2 = otp.analyst.Scoring.stepEdge(cutoff2);
            $("#cutoff1").text(labels[0]);
            $("#cutoff2").text(labels[1]);
            for (var i = 0; i < gui.populations.length; i++) {
                var pop = gui.populations[i];
                $("#" + pop.key + "1").text(scorer.score(timeGrid, pop, edge1, 1.0));
                $("#" + pop.key + "2").text(scorer.score(timeGrid, pop, edge2, 1.0));
            }
        });

        if (false) {
            /* Get the cutoff times from the input, in minutes */
            gui.isochronesLayerGroup.clearLayers();
            var isotimes = [ 900, 1800, 2700, 3600 ];
            /* Get the isochrone GeoJSON features from the server */
            gui.isochrone = new otp.analyst.Isochrone(params1, isotimes).onLoad(function(iso) {
                for (var i = 0; i < isotimes.length; i++) {
                    var isoLayer = L.geoJson(iso.getFeature(isotimes[i]), {
                        style : {
                            color : "#0000FF",
                            weight : 2,
                            dashArray : (i % 2) == 1 ? "5,2" : "",
                            fillOpacity : 0.0,
                            fillColor : "#000000"
                        }
                    });
                    gui.isochronesLayerGroup.addLayer(isoLayer);
                }
            });
        }
    }

    /* Plug the refresh callback function. */
    gui.widget1.onRefresh(refresh);
    $("#refresh").click(refresh);
    /* Refresh to force an initial load. */
    gui.widget1.refresh();

});
