package com.asamm.locus.addon.graphhopper;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.ViaInstruction;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import locus.api.android.features.computeTrack.ComputeTrackParameters;
import locus.api.android.features.computeTrack.ComputeTrackService;
import locus.api.android.utils.LocusUtils;
import locus.api.objects.extra.ExtraData;
import locus.api.objects.extra.Location;
import locus.api.objects.extra.Track;
import locus.api.objects.extra.Waypoint;
import locus.api.utils.Logger;

/**
 * Created by menion on 9. 7. 2014.
 * Class is part of Locus project
 */
public class RoutingService extends ComputeTrackService {

	// tag for logger
    private static final String TAG = "RoutingService";

    // instance of GraphHooper engine
    private GraphHopper mHopper;
	// remember last used map path
	private File mLastRoutingItem;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public String getAttribution() {
        return "Powered by <a href=\"http://graphhopper.com/#enterprise\">GraphHopper API</a>";
    }

    @Override
    public int[] getTrackTypes() {
        // initialize graphHopper instance
        if (getGraphHooper() == null) {
            Logger.logW(TAG, "getTrackTypes()," +
                    "unable to initialize GraphHopper instance");
            return null;
        }

        // load encoders
        EncodingManager em = getGraphHooper().getEncodingManager();
        List<Integer> types = new ArrayList<>();

        // get all encoders
        for (FlagEncoder enc : em.fetchEdgeEncoders()) {
            String encType = enc.toString();

            // add car
            switch (encType) {
                case FlagEncoderFactory.CAR:
                    types.add(ExtraData.VALUE_RTE_TYPE_CAR_FAST);
                    types.add(ExtraData.VALUE_RTE_TYPE_CAR_SHORT);
                    break;
                case FlagEncoderFactory.MOTORCYCLE:
                    types.add(ExtraData.VALUE_RTE_TYPE_MOTORCYCLE);
                    break;
                case FlagEncoderFactory.BIKE:
                    types.add(ExtraData.VALUE_RTE_TYPE_CYCLE_FAST);
                    types.add(ExtraData.VALUE_RTE_TYPE_CYCLE_SHORT);
                    break;
                case FlagEncoderFactory.MOUNTAINBIKE:
                    types.add(ExtraData.VALUE_RTE_TYPE_CYCLE_MTB);
                    break;
                case FlagEncoderFactory.RACINGBIKE:
                    types.add(ExtraData.VALUE_RTE_TYPE_CYCLE_RACING);
                    break;
                case FlagEncoderFactory.FOOT:
                    types.add(ExtraData.VALUE_RTE_TYPE_FOOT);
                    break;
                default:
                    Logger.logW(TAG, "getTrackTypes()," +
                            "unsupported type:" + encType);
                    break;
            }
        }

        // convert to array and return
        int[] typesA = new int[types.size()];
        for (int i = 0, m = types.size(); i < m; i++) {
            typesA[i] = types.get(i);
        }
        return typesA;
    }

    @Override
    public Intent getIntentForSettings() {
        Intent intent = new Intent();
        intent.setClass(this, SettingsActivity.class);
        return intent;
    }

    @Override
    public int getNumOfTransitPoints() {
        return 2;
    }

    @Override
    public Track computeTrack(LocusUtils.LocusVersion lv, ComputeTrackParameters params) {
        if (getGraphHooper() == null) {
            Logger.logW(TAG, "computeTrack(" + params + ")," +
                    "unable to initialize GraphHopper instance");
            return null;
        }

        // define vehicle
        String vehicle = FlagEncoderFactory.CAR;
		String weighting = "";
		switch (params.getType()) {
			case ExtraData.VALUE_RTE_TYPE_CAR_FAST:
				vehicle = FlagEncoderFactory.CAR;
				weighting = "fastest";
				break;
			case ExtraData.VALUE_RTE_TYPE_CAR_SHORT:
				vehicle = FlagEncoderFactory.CAR;
				weighting = "shortest";
				break;
            case ExtraData.VALUE_RTE_TYPE_MOTORCYCLE:
                vehicle = FlagEncoderFactory.MOTORCYCLE;
                break;
            case ExtraData.VALUE_RTE_TYPE_CYCLE_FAST:
				vehicle = FlagEncoderFactory.BIKE;
				weighting = "fastest";
				break;
			case ExtraData.VALUE_RTE_TYPE_CYCLE_SHORT:
				vehicle = FlagEncoderFactory.BIKE;
				weighting = "shortest";
				break;
            case ExtraData.VALUE_RTE_TYPE_CYCLE_MTB:
                vehicle = FlagEncoderFactory.MOUNTAINBIKE;
                break;
            case ExtraData.VALUE_RTE_TYPE_CYCLE_RACING:
                vehicle = FlagEncoderFactory.RACINGBIKE;
                break;
            case ExtraData.VALUE_RTE_TYPE_FOOT:
				vehicle = FlagEncoderFactory.FOOT;
				break;
		}

        // finally compute
        return calcPath(params.getLocations(),
                vehicle, weighting,
                params.hasDirection() ? params.getCurrentDirection() : Double.NaN,
                params.isComputeInstructions());
    }

    /**
     * Lazy loader from compute engine
     * @return instance of GraphHooper service
     */
    private GraphHopper getGraphHooper() {
		// check current selected map
		File routingItem = Utils.getCurrentRoutingItem(this);
		if (routingItem == null) {
			throw new IllegalArgumentException("No valid routing item selected");
		}

		// initialize GraphHooper if required
		if (mHopper == null || mLastRoutingItem == null || !routingItem.equals(mLastRoutingItem)) {
            GraphHopper gh = new GraphHopper().forMobile();
            gh.setCHEnabled(false);
            gh.setEnableInstructions(true);
            gh.setAllowWrites(false);
            boolean load = gh.load(routingItem.getAbsolutePath());
            Logger.logD(TAG, "found graph " + gh.getGraphHopperStorage() + ", " +
                    "nodes:" + gh.getGraphHopperStorage().getNodes() + ", " +
                    "path:" + routingItem.getAbsolutePath() + ", " +
                    "load:" + load);

			// store result
            if (load) {
                mHopper = gh;
                mLastRoutingItem = routingItem;
            } else {
                mHopper = null;
            }
        }

        // return initialized GraphHopper
        return mHopper;
    }

    private Track calcPath(Location[] locs, String vehicle, String weighting,
            double firstPointDirection, boolean instructions) {
        Logger.logD(TAG, "calculating path for " + vehicle);
        StopWatch sw = new StopWatch().start();

        // define request and it's parameters
        GHRequest req = new GHRequest(2);
        for (int i = 0, m = locs.length; i < m; i++) {
            Location loc = locs[i];
            if (i == 0) {
                req.addPoint(new GHPoint(loc.getLatitude(), loc.getLongitude()), firstPointDirection);
            } else {
                req.addPoint(new GHPoint(loc.getLatitude(), loc.getLongitude()));
            }
        }
        req.setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
        req.setVehicle(vehicle);
        req.setWeighting(weighting);
        req.getHints().
                put("instructions", instructions).
                put("douglas.minprecision", 1);

        // execute request
        GHResponse resp = mHopper.route(req);
        float time = sw.stop().getSeconds();

        if (!resp.hasErrors()) {
			PathWrapper path = resp.getBest();
            Logger.logD(TAG, "found path with distance:" + path.getDistance()
                    / 1000f + ", nodes:" + path.getPoints().getSize() + ", time:"
                    + time + " " + resp.getDebugInfo());
            Logger.logD(TAG, "the route is " + (int) (path.getDistance() / 100) / 10f
                    + "km long, time:" + path.getTime() / 60000f + "min, debug:" + time);
            return createTrackFromResult(resp, instructions);
        } else {
			// notify on problems
			List<Throwable> errors = resp.getErrors();
			for (int i = 0, m = errors.size(); i < m; i++) {
				final Throwable error = errors.get(i);
				if (error instanceof IllegalArgumentException &&
						error.getMessage().contains("Cannot find point ")) {
					Handler handler = new Handler(Looper.getMainLooper());
					handler.post(new Runnable() {

						@Override
						public void run() {
							Toast.makeText(RoutingService.this, error.getMessage(), Toast.LENGTH_LONG).show();
						}
					});
				}
			}

			// print result and return
            Logger.logW(TAG, "Error:" + resp.getErrors());
            return null;
        }
    }

    private Track createTrackFromResult(GHResponse resp, boolean instructions) {
        // create new track
        Track track = new Track();
		PathWrapper path = resp.getBest();

        // add points by instructions if exists
        if (instructions) {
            int instrCount = path.getInstructions().size();
            ViaInstruction viaInstruction = null;
            for (int i = 0; i < instrCount; i++) {
                // get parameters
                Instruction inst = path.getInstructions().get(i);
                PointList points = inst.getPoints();
                Location firstLoc = createLocation(points, 0);

                // store "via instruction" on next cycle
                if (inst instanceof ViaInstruction) {
                    viaInstruction = (ViaInstruction) inst;
                    continue;
                }

                // create instruction waypoint
                Waypoint wpt = new Waypoint();
                wpt.setLocation(firstLoc);
                wpt.addParameter(ExtraData.PAR_RTE_POINT_ACTION,
                        graphHopperActionToLocus(inst));

                // set name
                if (inst.getName().length() > 0) {
                    wpt.addParameter(ExtraData.PAR_RTE_STREET, inst.getName());
                }

                // set speed and distance parameters
                wpt.addParameter(ExtraData.PAR_RTE_DISTANCE_F,
                        Float.toString((float) inst.getDistance()));
                wpt.addParameter(ExtraData.PAR_RTE_TIME_I,
                        (int) (inst.getTime() / 1000.0));
                track.getWaypoints().add(wpt);

                // use "Via instruction"
                if (viaInstruction != null) {
                    wpt.addParameter(ExtraData.PAR_RTE_POINT_ACTION,
                            ExtraData.VALUE_RTE_ACTION_PASS_PLACE);
                    viaInstruction = null;
                }

                // add points from instruction. Add also first point, so waypoint can be correctly indexed
                // and attached to correct point on track
                addPointsToTrack(track, inst.getPoints());
            }
        }

        // check if track already contain any data. If so, they're from instructions,
        // so we don't need to continue
        if (track.getPointsCount() > 0) {
            return track;
        }

        // add all points
        addPointsToTrack(track, path.getPoints());

        // return generated track
        return track;
    }

    private void addPointsToTrack(Track track, PointList points) {
        for (int i = 0, m = points.size(); i < m; i++) {
            track.getPoints().add(createLocation(points, i));
        }
    }

    /**
     * Generate simple location object from coordinates.
     * @param points list of points
     * @param index index of wanted point
     * @return generated location
     */
    private Location createLocation(PointList points, int index) {
        Location loc = new Location();
        loc.setLatitude(points.getLatitude(index));
        loc.setLongitude(points.getLongitude(index));
        return loc;
    }

    /**
     * Convert GraphHopper action to Locus action.
     * @param inst instruction on certain place
     * @return code of action in Locus system
     */
    private int graphHopperActionToLocus(Instruction inst) {
        // get roundabout
        if (inst.getSign() == Instruction.USE_ROUNDABOUT) {

            // handle special instruction. If roundabout is not defined by it, rather ignore it,
            // then notify incorrect exit
            if (inst instanceof RoundaboutInstruction) {
                RoundaboutInstruction instRb = (RoundaboutInstruction) inst;
                return ExtraData.VALUE_RTE_ACTION_ROUNDABOUT_EXIT_1 +
                        (instRb.getExitNumber() - 1);
            } else {
                Logger.logW(TAG, "graphHopperActionToLocus(" + inst + "), " +
                        "invalid Roundabout instruction");
                return ExtraData.VALUE_RTE_ACTION_NO_MANEUVER;
            }
        }

        // handle common instructions
        switch(inst.getSign()) {

            // TURN RIGHT

            case Instruction.TURN_SLIGHT_RIGHT:
                return ExtraData.VALUE_RTE_ACTION_RIGHT_SLIGHT;
            case Instruction.TURN_RIGHT:
                return ExtraData.VALUE_RTE_ACTION_RIGHT;
            case Instruction.TURN_SHARP_RIGHT:
                return ExtraData.VALUE_RTE_ACTION_RIGHT_SHARP;

            // TURN LEFT

            case Instruction.TURN_SLIGHT_LEFT:
                return ExtraData.VALUE_RTE_ACTION_LEFT_SLIGHT;
            case Instruction.TURN_LEFT:
                return ExtraData.VALUE_RTE_ACTION_LEFT;
            case Instruction.TURN_SHARP_LEFT:
                return ExtraData.VALUE_RTE_ACTION_LEFT_SHARP;

            // VARIOUS

            case Instruction.CONTINUE_ON_STREET:
                return ExtraData.VALUE_RTE_ACTION_CONTINUE_STRAIGHT;
            case Instruction.FINISH:
                return ExtraData.VALUE_RTE_ACTION_ARRIVE_DEST;
            case Instruction.REACHED_VIA:
                return ExtraData.VALUE_RTE_ACTION_PASS_PLACE;

            // IGNORED

            case Instruction.LEAVE_ROUNDABOUT:
                return ExtraData.VALUE_RTE_ACTION_NO_MANEUVER;
            default:
                return ExtraData.VALUE_RTE_ACTION_NO_MANEUVER;
        }
    }
}
