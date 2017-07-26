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
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.ViaInstruction;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import locus.api.android.features.computeTrack.ComputeTrackParameters;
import locus.api.android.features.computeTrack.ComputeTrackService;
import locus.api.android.utils.LocusUtils;
import locus.api.objects.enums.PointRteAction;
import locus.api.objects.extra.GeoDataExtra;
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

	// flag if we have "bike2" mode in latest loaded file
	private boolean mBike2Encoding;

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
		List<Weighting> weightings = getGraphHooper().getCHFactoryDecorator().getWeightings();
        List<Integer> types = new ArrayList<>();

        // get all encoders
        for (FlagEncoder enc : em.fetchEdgeEncoders()) {
            String encType = enc.toString();

            // add car
            switch (encType) {
                case FlagEncoderFactory.CAR:
                	boolean carAdded = false;
                	for (Weighting weighting : weightings) {
						if (weighting instanceof FastestWeighting) {
							addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR_FAST, types);
							carAdded = true;
						} else if (weighting instanceof ShortestWeighting) {
							addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR_SHORT, types);
							carAdded = true;
						}
					}

					// add basic "car" if no car type exists
					if (!carAdded) {
						addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CAR, types);
					}
                    break;
                case FlagEncoderFactory.MOTORCYCLE:
                    addTrackType(GeoDataExtra.VALUE_RTE_TYPE_MOTORCYCLE, types);
                    break;
                case FlagEncoderFactory.BIKE:
				case FlagEncoderFactory.BIKE2:
                	boolean bikeAdded = false;
					for (Weighting weighting : weightings) {
						if (weighting instanceof FastestWeighting) {
							addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_FAST, types);
							bikeAdded = true;
						} else if (weighting instanceof ShortestWeighting) {
							addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_SHORT, types);
							bikeAdded = true;
						}
					}

					// add basic "bike" if no bike type exists
					if (!bikeAdded) {
						addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE, types);
					}

					// enable "Bike2" encoding
					if (encType.equalsIgnoreCase(FlagEncoderFactory.BIKE2)) {
						mBike2Encoding = true;
					}
					break;
                case FlagEncoderFactory.MOUNTAINBIKE:
                    addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_MTB, types);
                    break;
                case FlagEncoderFactory.RACINGBIKE:
                    addTrackType(GeoDataExtra.VALUE_RTE_TYPE_CYCLE_RACING, types);
                    break;
                case FlagEncoderFactory.FOOT:
                    addTrackType(GeoDataExtra.VALUE_RTE_TYPE_FOOT_01, types);
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

	/**
	 * Add certain type into possible routing types.
	 * @param type type to add
	 * @param types types container
	 */
	private void addTrackType(int type, List<Integer> types) {
		if (!types.contains(type)) {
			types.add(type);
		}
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
			case GeoDataExtra.VALUE_RTE_TYPE_CAR:
				vehicle = FlagEncoderFactory.CAR;
				break;
			case GeoDataExtra.VALUE_RTE_TYPE_CAR_FAST:
				vehicle = FlagEncoderFactory.CAR;
				weighting = "fastest";
				break;
			case GeoDataExtra.VALUE_RTE_TYPE_CAR_SHORT:
				vehicle = FlagEncoderFactory.CAR;
				weighting = "shortest";
				break;
            case GeoDataExtra.VALUE_RTE_TYPE_MOTORCYCLE:
                vehicle = FlagEncoderFactory.MOTORCYCLE;
                break;
            case GeoDataExtra.VALUE_RTE_TYPE_CYCLE:
            	if (mBike2Encoding) {
					vehicle = FlagEncoderFactory.BIKE2;
				} else {
					vehicle = FlagEncoderFactory.BIKE;
				}
				break;
            case GeoDataExtra.VALUE_RTE_TYPE_CYCLE_FAST:
				if (mBike2Encoding) {
					vehicle = FlagEncoderFactory.BIKE2;
				} else {
					vehicle = FlagEncoderFactory.BIKE;
				}
				weighting = "fastest";
				break;
			case GeoDataExtra.VALUE_RTE_TYPE_CYCLE_SHORT:
				if (mBike2Encoding) {
					vehicle = FlagEncoderFactory.BIKE2;
				} else {
					vehicle = FlagEncoderFactory.BIKE;
				}
				weighting = "shortest";
				break;
            case GeoDataExtra.VALUE_RTE_TYPE_CYCLE_MTB:
                vehicle = FlagEncoderFactory.MOUNTAINBIKE;
                break;
            case GeoDataExtra.VALUE_RTE_TYPE_CYCLE_RACING:
                vehicle = FlagEncoderFactory.RACINGBIKE;
                break;
            case GeoDataExtra.VALUE_RTE_TYPE_FOOT_01:
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
		try {
			if (mHopper == null || mLastRoutingItem == null || !routingItem.equals(mLastRoutingItem)) {
				// reset parameters
				mBike2Encoding = false;

				// initialize graphHopper
				GraphHopper gh = new GraphHopper().forMobile();
				setGraphHopperProperties(gh, routingItem);
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
		} catch (IOException e) {
			Logger.logE(TAG, "getGraphHooper()", e);
			mHopper = null;
		}

        // return initialized GraphHopper
        return mHopper;
    }

	/**
	 * Set parameters based on properties file.
	 * @param gh graphHopper instance
	 * @throws IOException excetion in case of any problems with loading properties file
	 */
	private void setGraphHopperProperties(GraphHopper gh, File routingItem) throws IOException {
		// set default parameters
		gh.setEnableInstructions(true);
		gh.setAllowWrites(false);
		gh.setCHEnabled(false);
		gh.setElevation(false);

		// load properties file
		byte[] filePropData = readFile(new File(routingItem, "properties"));
		if (filePropData == null || filePropData.length == 0) {
			return;
		}
		String fileProp = new String(filePropData);

		// test weighting
		Pattern patternWei = Pattern.compile("graph\\.ch\\.weightings=\\[(.*)\\]");
		Matcher matcherWei = patternWei.matcher(fileProp);
		if (matcherWei.find()) {
			gh.setCHEnabled(matcherWei.group(1).length() > 0);
		}

		// test elevation
		Pattern patternEle = Pattern.compile("graph\\.dimension=(\\d)");
		Matcher matcherEle = patternEle.matcher(fileProp);
		if (matcherEle.find()) {
			gh.setElevation(Integer.parseInt(matcherEle.group(1)) > 2);
		}
	}

	/**
	 * Read content of a file into in-memory byte array.
	 * @param file file to load
	 * @return loaded data
	 * @throws IOException exception in case of any problem
	 */
	private byte[] readFile(File file) throws IOException {
		// Open file
		RandomAccessFile f = null;
		try {
			// Get and check length
			f = new RandomAccessFile(file, "r");
			long longlength = f.length();
			int length = (int) longlength;
			if (length != longlength)
				throw new IOException("File size >= 2 GB");
			// Read file and return data
			byte[] data = new byte[length];
			f.readFully(data);
			return data;
		} finally {
			if (f != null) {
				f.close();
			}
		}
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
                wpt.addParameter(GeoDataExtra.PAR_RTE_POINT_ACTION,
                        graphHopperActionToLocus(inst).getId());

                // set name
                if (inst.getName().length() > 0) {
                    wpt.addParameter(GeoDataExtra.PAR_RTE_STREET, inst.getName());
                }

                // set speed and distance parameters
                wpt.addParameter(GeoDataExtra.PAR_RTE_DISTANCE_F,
                        Float.toString((float) inst.getDistance()));
                wpt.addParameter(GeoDataExtra.PAR_RTE_TIME_I,
                        (int) (inst.getTime() / 1000.0));
                track.getWaypoints().add(wpt);

                // use "Via instruction"
                if (viaInstruction != null) {
                    wpt.addParameter(GeoDataExtra.PAR_RTE_POINT_ACTION,
							PointRteAction.PASS_PLACE.getId());
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
    private PointRteAction graphHopperActionToLocus(Instruction inst) {
        // get roundabout
        if (inst.getSign() == Instruction.USE_ROUNDABOUT) {

            // handle special instruction. If roundabout is not defined by it, rather ignore it,
            // then notify incorrect exit
            if (inst instanceof RoundaboutInstruction) {
                RoundaboutInstruction instRb = (RoundaboutInstruction) inst;
                return PointRteAction.getActionRoundabout(instRb.getExitNumber());
            } else {
                Logger.logW(TAG, "graphHopperActionToLocus(" + inst + "), " +
                        "invalid Roundabout instruction");
                return PointRteAction.NO_MANEUVER;
            }
        }

        // handle common instructions
        switch(inst.getSign()) {

            // TURN RIGHT

            case Instruction.TURN_SLIGHT_RIGHT:
                return PointRteAction.RIGHT_SLIGHT;
            case Instruction.TURN_RIGHT:
                return PointRteAction.RIGHT;
            case Instruction.TURN_SHARP_RIGHT:
                return PointRteAction.RIGHT_SHARP;

            // TURN LEFT

            case Instruction.TURN_SLIGHT_LEFT:
                return PointRteAction.LEFT_SLIGHT;
            case Instruction.TURN_LEFT:
                return PointRteAction.LEFT;
            case Instruction.TURN_SHARP_LEFT:
                return PointRteAction.LEFT_SHARP;

            // VARIOUS

            case Instruction.CONTINUE_ON_STREET:
                return PointRteAction.CONTINUE_STRAIGHT;
			case Instruction.KEEP_RIGHT:
				return PointRteAction.STAY_RIGHT;
			case Instruction.KEEP_LEFT:
				return PointRteAction.STAY_LEFT;

            case Instruction.FINISH:
                return PointRteAction.ARRIVE_DEST;
            case Instruction.REACHED_VIA:
                return PointRteAction.PASS_PLACE;

            // IGNORED

            case Instruction.LEAVE_ROUNDABOUT:
                return PointRteAction.NO_MANEUVER;
            default:
                return PointRteAction.NO_MANEUVER;
        }
    }
}
