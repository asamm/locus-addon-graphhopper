/**
 * Created by menion on 9. 7. 2014.
 * Class is part of Locus project
 */
package com.asamm.locus.addon.graphhopper

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.routing.util.DefaultFlagEncoderFactory
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.routing.util.FlagEncoderFactory
import com.graphhopper.routing.weighting.FastestWeighting
import com.graphhopper.routing.weighting.ShortestWeighting
import com.graphhopper.storage.RAMDirectory
import com.graphhopper.storage.StorableProperties
import com.graphhopper.util.*
import com.graphhopper.util.shapes.GHPoint
import locus.api.android.features.computeTrack.ComputeTrackParameters
import locus.api.android.features.computeTrack.ComputeTrackService
import locus.api.android.objects.LocusVersion
import locus.api.objects.extra.GeoDataExtra
import locus.api.objects.extra.Location
import locus.api.objects.extra.PointRteAction
import locus.api.objects.geoData.Point
import locus.api.objects.geoData.Track
import locus.api.utils.Logger
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Main routing service that do all heavy work with compute of required route.
 */
class RoutingService : ComputeTrackService() {

    // instance of GraphHooper engine
    private var hopper: GraphHopper? = null
        get() {
            val routingItem = Utils.getCurrentRoutingItem(this)?.absoluteFile
                    ?: throw IllegalArgumentException("No valid routing item selected")
            try {
                // check current selected map
                if (field == null || routingItem != lastRoutingItem) {
                    bike2Encoding = false

                    // initialize GraphHooper
                    val gh = GraphHopper().forMobile()

                    // reset parameters
                    setGraphHopperProperties(gh, routingItem)

                    // initialize graphHopper
                    val load = gh.load(routingItem.path)
                    Logger.logD(TAG, "found graph ${gh.graphHopperStorage}, " +
                            "nodes: ${gh.graphHopperStorage.nodes}, " +
                            "path: ${routingItem.path}, " +
                            "load: $load")

                    // store result
                    if (load) {
                        field = gh
                        lastRoutingItem = routingItem
                    } else {
                        field = null
                    }
                }
            } catch (e: IOException) {
                Logger.logE(TAG, "getGraphHooper()", e)
                field = null
            }

            // return initialized GraphHopper
            return field
        }

    // remember last used map path
    private var lastRoutingItem: File? = null

    // flag if we have "bike2" mode in latest loaded file
    private var bike2Encoding = false

    override val attribution: String
        get() = "Powered by <a href=\"https://graphhopper.com/#enterprise\">GraphHopper API</a>"

    override val trackTypes: IntArray
        get() {
            // initialize graphHopper instance
            val hopper = hopper
            if (hopper == null) {
                Logger.logW(TAG, "getTrackTypes(), unable to initialize GraphHopper instance")
                return IntArray(0)
            }

            // load encoders
            val types = ArrayList<Int>()
            val em = hopper.encodingManager
            val profiles = hopper.chFactoryDecorator.chProfiles

            // get all encoders
            for (enc in em.fetchEdgeEncoders()) {

                // add car
                when (val encType = enc.toString()) {
                    FlagEncoderFactory.BIKE,
                    FlagEncoderFactory.BIKE2 -> {
                        var bikeAdded = false
                        for (profile in profiles) {
                            val weighting = profile.weighting
                            if (weighting is FastestWeighting) {
                                addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_FAST, types)
                                bikeAdded = true
                            } else if (weighting is ShortestWeighting) {
                                addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_SHORT, types)
                                bikeAdded = true
                            }
                        }

                        // add basic "bike" if no bike type exists
                        if (!bikeAdded) {
                            addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE, types)
                        }

                        // enable "Bike2" encoding
                        if (encType.equals(FlagEncoderFactory.BIKE2, ignoreCase = true)) {
                            bike2Encoding = true
                        }
                    }
                    FlagEncoderFactory.CAR -> {
                        var carAdded = false
                        for (profile in profiles) {
                            val weighting = profile.weighting
                            if (weighting is FastestWeighting) {
                                addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR_FAST, types)
                                carAdded = true
                            } else if (weighting is ShortestWeighting) {
                                addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR_SHORT, types)
                                carAdded = true
                            }
                        }

                        // add basic "car" if no car type exists
                        if (!carAdded) {
                            addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR, types)
                        }
                    }
                    FlagEncoderFactory.FOOT -> {
                        addTrackType(GeoDataExtra.VALUE_RTE_TYPE_FOOT_01, types)
                    }
                    FlagEncoderFactory.HIKE -> {
                        addTrackType(GeoDataExtra.VALUE_RTE_TYPE_FOOT_02, types)
                    }
                    FlagEncoderFactory.MOTORCYCLE -> {
                        addTrackType(GeoDataExtra.VALUE_RTE_TYPE_MOTORCYCLE, types)
                    }
                    FlagEncoderFactory.MOUNTAINBIKE -> {
                        addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_MTB, types)
                    }
                    FlagEncoderFactory.RACINGBIKE -> {
                        addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_RACING, types)
                    }
                    else -> Logger.logW(TAG, "getTrackTypes(), unsupported type:$encType")
                }
            }

            // convert to array and return
            return types.toIntArray()
        }

    /**
     * Add certain type into possible routing types.
     *
     * @param type  type to add
     * @param types types container
     */
    private fun addTrackType(type: Int, types: MutableList<Int>) {
        if (!types.contains(type)) {
            types.add(type)
        }
    }

    override val intentForSettings: Intent
        get() = Intent().apply {
            setClass(this@RoutingService, SettingsActivity::class.java)
        }

    override fun computeTrack(lv: LocusVersion?, params: ComputeTrackParameters): Track? {
        // check instance of GraphHopper
        if (hopper == null) {
            Logger.logW(TAG, "computeTrack($params), unable to initialize GraphHopper instance")
            return null
        }

        // define vehicle
        var vehicle = FlagEncoderFactory.CAR
        var weighting = ""
        when (params.type) {
            GeoDataExtra.VALUE_RTE_TYPE_CAR -> {
                vehicle = FlagEncoderFactory.CAR
            }
            GeoDataExtra.VALUE_RTE_TYPE_CAR_FAST -> {
                vehicle = FlagEncoderFactory.CAR
                weighting = "fastest"
            }
            GeoDataExtra.VALUE_RTE_TYPE_CAR_SHORT -> {
                vehicle = FlagEncoderFactory.CAR
                weighting = "shortest"
            }
            GeoDataExtra.VALUE_RTE_TYPE_CYCLE -> {
                vehicle = if (bike2Encoding) {
                    FlagEncoderFactory.BIKE2
                } else {
                    FlagEncoderFactory.BIKE
                }
            }
            GeoDataExtra.VALUE_RTE_TYPE_CYCLE_FAST -> {
                vehicle = if (bike2Encoding) {
                    FlagEncoderFactory.BIKE2
                } else {
                    FlagEncoderFactory.BIKE
                }
                weighting = "fastest"
            }
            GeoDataExtra.VALUE_RTE_TYPE_CYCLE_SHORT -> {
                vehicle = if (bike2Encoding) {
                    FlagEncoderFactory.BIKE2
                } else {
                    FlagEncoderFactory.BIKE
                }
                weighting = "shortest"
            }
            GeoDataExtra.VALUE_RTE_TYPE_CYCLE_MTB -> {
                vehicle = FlagEncoderFactory.MOUNTAINBIKE
            }
            GeoDataExtra.VALUE_RTE_TYPE_CYCLE_RACING -> {
                vehicle = FlagEncoderFactory.RACINGBIKE
            }
            GeoDataExtra.VALUE_RTE_TYPE_FOOT_01 -> {
                vehicle = FlagEncoderFactory.FOOT
            }
            GeoDataExtra.VALUE_RTE_TYPE_FOOT_02 -> {
                vehicle = FlagEncoderFactory.HIKE
            }
            GeoDataExtra.VALUE_RTE_TYPE_MOTORCYCLE -> {
                vehicle = FlagEncoderFactory.MOTORCYCLE
            }
        }

        // finally compute
        return calcPath(params.locations,
                vehicle, weighting,
                if (params.hasDirection) params.currentDirection.toDouble() else java.lang.Double.NaN,
                params.isComputeInstructions)
    }

    private fun calcPath(locs: Array<Location>, vehicle: String, weighting: String,
            firstPointDirection: Double, instructions: Boolean): Track? {
        Logger.logD(TAG, "calculating path for $vehicle")
        val sw = StopWatch().start()

        // define request and it's parameters
        val req = GHRequest(2)
        for (i in locs.indices) {
            val loc = locs[i]
            if (i == 0) {
                req.addPoint(GHPoint(loc.latitude, loc.longitude), firstPointDirection)
            } else {
                req.addPoint(GHPoint(loc.latitude, loc.longitude))
            }
        }

        req.vehicle = vehicle
        req.weighting = weighting
        req.hints.put("instructions", instructions).put("douglas.minprecision", 1)

        // execute request
        val resp = hopper!!.route(req)
        val time = sw.stop().seconds

        if (!resp.hasErrors()) {
            val path = resp.best
            Logger.logD(TAG, "found path with " +
                    "distance:" + path.distance / 1000f + ", " +
                    "nodes: ${path.points.size}, " +
                    "time: $time, ${resp.debugInfo}")
            Logger.logD(TAG, "the route " +
                    "distance: ${(path.distance / 100).toInt() / 10f} km, " +
                    "time: ${path.time / 60000f} min, " +
                    "debug: $time")
            return createTrackFromResult(resp, instructions)
        } else {
            // notify on problems
            val errors = resp.errors
            for (i in 0 until errors.size) {
                val error = errors[i]
                if (error is IllegalArgumentException && error.message?.contains("Cannot find point ") == true) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { Toast.makeText(this@RoutingService, error.message, Toast.LENGTH_LONG).show() }
                }
            }

            // print result and return
            Logger.logW(TAG, "Error:" + resp.errors)
            return null
        }
    }

    private fun createTrackFromResult(resp: GHResponse, instructions: Boolean): Track {
        // create new track
        val track = Track()
        val path = resp.best

        // add points by instructions if exists
        if (instructions) {
            val instrCount = path.instructions.size
            var viaInstruction: ViaInstruction? = null
            for (i in 0 until instrCount) {
                // get parameters
                val inst = path.instructions[i]
                val points = inst.points
                val firstLoc = createLocation(points, 0)

                // store "via instruction" on next cycle
                if (inst is ViaInstruction) {
                    viaInstruction = inst
                    continue
                }

                // create instruction waypoint
                val pt = Point()
                pt.location = firstLoc
                pt.addParameter(GeoDataExtra.PAR_RTE_POINT_ACTION,
                        graphHopperActionToLocus(inst).id)

                // set name
                if (inst.name.isNotEmpty()) {
                    pt.addParameter(GeoDataExtra.PAR_RTE_STREET, inst.name)
                }

                // set speed and distance parameters
                pt.addParameter(GeoDataExtra.PAR_RTE_DISTANCE_F,
                        inst.distance.toString())
                pt.addParameter(GeoDataExtra.PAR_RTE_TIME_I,
                        (inst.time / 1000.0).toInt())
                track.waypoints.add(pt)

                // use "Via instruction"
                if (viaInstruction != null) {
                    pt.addParameter(GeoDataExtra.PAR_RTE_POINT_ACTION,
                            PointRteAction.PASS_PLACE.id)
                    viaInstruction = null
                }

                // add points from instruction. Add also first point, so waypoint can be correctly indexed
                // and attached to correct point on track
                addPointsToTrack(track, inst.points)
            }
        }

        // check if track already contain any data. If so, they're from instructions,
        // so we don't need to continue
        if (track.pointsCount > 0) {
            return track
        }

        // add all points
        addPointsToTrack(track, path.points)

        // return generated track
        return track
    }

    /**
     * Add pack of generated points into existing track.
     *
     * @param track track object
     * @param points loaded points
     */
    private fun addPointsToTrack(track: Track, points: PointList) {
        for (i in 0 until points.size) {
            track.points.add(createLocation(points, i))
        }
    }

    /**
     * Generate simple location object from coordinates.
     *
     * @param points list of points
     * @param index  index of wanted point
     * @return generated location
     */
    private fun createLocation(points: PointList, index: Int): Location {
        return Location().apply {
            longitude = points.getLongitude(index)
            latitude = points.getLatitude(index)
        }
    }

    /**
     * Convert GraphHopper action to Locus action.
     *
     * @param inst instruction on certain place
     * @return code of action in Locus system
     */
    private fun graphHopperActionToLocus(inst: Instruction): PointRteAction {
        // get roundabout
        if (inst.sign == Instruction.USE_ROUNDABOUT) {

            // handle special instruction. If roundabout is not defined by it, rather ignore it,
            // then notify incorrect exit
            return if (inst is RoundaboutInstruction) {
                PointRteAction.getActionRoundabout(inst.exitNumber)
            } else {
                Logger.logW(TAG, "graphHopperActionToLocus($inst), invalid Roundabout instruction")
                PointRteAction.NO_MANEUVER
            }
        }

        // handle common instructions
        when (inst.sign) {

            // TURN RIGHT

            Instruction.TURN_SLIGHT_RIGHT -> return PointRteAction.RIGHT_SLIGHT
            Instruction.TURN_RIGHT -> return PointRteAction.RIGHT
            Instruction.TURN_SHARP_RIGHT -> return PointRteAction.RIGHT_SHARP

            // TURN LEFT

            Instruction.TURN_SLIGHT_LEFT -> return PointRteAction.LEFT_SLIGHT
            Instruction.TURN_LEFT -> return PointRteAction.LEFT
            Instruction.TURN_SHARP_LEFT -> return PointRteAction.LEFT_SHARP

            // VARIOUS

            Instruction.CONTINUE_ON_STREET -> return PointRteAction.CONTINUE_STRAIGHT
            Instruction.KEEP_RIGHT -> return PointRteAction.STAY_RIGHT
            Instruction.KEEP_LEFT -> return PointRteAction.STAY_LEFT

            Instruction.FINISH -> return PointRteAction.ARRIVE_DEST
            Instruction.REACHED_VIA -> return PointRteAction.PASS_PLACE

            // IGNORED

            Instruction.LEAVE_ROUNDABOUT -> return PointRteAction.NO_MANEUVER
            else -> return PointRteAction.NO_MANEUVER
        }
    }

    //*************************************************
    // ENGINE
    //*************************************************

    /**
     * Set parameters based on properties file.
     *
     * @param gh graphHopper instance
     * @throws IOException exception in case of any problems with loading properties file
     */
    private fun setGraphHopperProperties(gh: GraphHopper, routingItem: File) {
        // set default parameters
        gh.isAllowWrites = false

        // load properties file
        val dir = RAMDirectory(routingItem.path, true)
        val properties = StorableProperties(dir)
        if (!properties.loadExisting()) {
            throw IllegalStateException("Cannot load properties to fetch EncodingManager configuration at: ${dir.location}")
        }

        // following code is not necessary - GraphHopper will create proper EncodingManager on demand,
        // but as in any case we load properties to configure CH and elevation, let's set EncodingManager explicitly
        val builder = EncodingManager.Builder()
        // enableInstructions is true by default, but to make sure
        builder.setEnableInstructions(true)
        properties.get("graph.encoded_values").takeIf { it.isNotBlank() }?.let {
            builder.addAll(gh.encodedValueFactory, it)
        }
        // GraphHopper doesn't expose flagEncoderFactory, but it is ok - stateless DefaultFlagEncoderFactory is used.
        properties.get("graph.flag_encoders").takeIf { it.isNotBlank() }?.let {
            builder.addAll(DefaultFlagEncoderFactory(), it)
        }

        gh.encodingManager = builder.build()

        gh.isCHEnabled = (properties.get("prepare.ch.done") == "true")
        gh.setElevation(properties.get("prepare.elevation_interpolation.done") == "true")
    }

    companion object {

        // tag for logger
        private const val TAG = "RoutingService"
    }
}
