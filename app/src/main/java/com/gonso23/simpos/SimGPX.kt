package com.gonso23.simpos


import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import android.net.Uri
import java.io.IOException
import kotlin.math.*
import kotlin.random.Random
import android.content.ContentResolver;
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.parcelableCreator

class GPXParser(private val uri: Uri, private val contentResolver: ContentResolver) {

    @Throws(XmlPullParserException::class, IOException::class)
    fun parseFile(): MutableList<Track> {
        val tracks = mutableListOf<Track>()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var track: Track? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "trk") {
                            track = Track()
                        } else if (tagName == "name" && track != null) {
                            track.name = parser.nextText()
                        } else if (track != null && tagName == "trkpt") {
                            val lat = parser.getAttributeValue(null, "lat").toDouble()
                            val lon = parser.getAttributeValue(null, "lon").toDouble()
                            var ele: Double? = null
                            if (parser.nextTag() == XmlPullParser.START_TAG && parser.name == "ele") {
                                ele = parser.nextText().toDouble()
                            }
                            track.addPoint(TrackPoint(lat, lon, ele))
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "trk" && track != null) {
                            tracks.add(track)
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return tracks
    }
}


@Parcelize
class SimTrack(
    val tracks: MutableList<Track>,
    var currentPointIndex: Int = 0,
    var selectedTrackIndex: Int = 0,
    var selectedMode: Mode? = null,
    var maxDistance: Double = 0.0,
    var errorVariance: Double = 0.0,
    var fileName: String? = null,
    var currentTrack: Track? = null,
    var lastPoint: TrackPoint? = null,
    val intermediatePoints: MutableList<TrackPoint> = mutableListOf()
) : Parcelable {

    override fun describeContents(): Int = 0


    init {
        if (tracks.isNotEmpty()) {
            this.currentTrack = tracks[0]
        }
    }

    enum class Mode {
        CURRENT_TRACK, LOOP_TRACK, LOOP_ALL
    }

    fun restartTrack(){
        currentPointIndex = 0
        intermediatePoints.clear()
    }

    fun selectTrack(index: Int): Boolean {
        if (index in tracks.indices && selectedTrackIndex != index) {
            selectedTrackIndex = index
            currentTrack = tracks[index]
            currentPointIndex = 0
            lastPoint = null
            intermediatePoints.clear()
            return true
        }
        return false
    }

    fun nextPoint(): TrackPoint? {
        if (intermediatePoints.isNotEmpty()) {
            lastPoint = intermediatePoints.removeAt(0)
            return lastPoint
        }

        var currentPoint: TrackPoint? = null

        when (selectedMode) {
            Mode.CURRENT_TRACK -> {
                currentTrack?.let {
                    if (currentPointIndex < it.points.size) {
                        currentPoint = it.points[currentPointIndex++]
                    } else {
                        currentPoint = null
                    }
                }
            }
            Mode.LOOP_TRACK -> {
                currentTrack?.let {
                    if (currentPointIndex < it.points.size) {
                        currentPoint = it.points[currentPointIndex++]
                    } else {
                        currentPointIndex = 0
                        currentPoint = it.points[currentPointIndex++]
                    }
                }
            }
            Mode.LOOP_ALL -> {
                if (currentTrack != null) {
                    if (currentPointIndex < currentTrack!!.points.size) {
                        currentPoint = currentTrack!!.points[currentPointIndex++]
                    } else {
                        val currentIndex = tracks.indexOf(currentTrack)
                        if (currentIndex < tracks.size - 1) {
                            selectTrack(currentIndex + 1)
                        } else {
                            selectTrack(0)
                        }
                        currentPoint = currentTrack!!.points[currentPointIndex++]
                    }
                } else {
                    currentPoint = null
                }
            }

            null -> {}
        }

        if (lastPoint != null && currentPoint != null && maxDistance > 0 ) {
            val im = calculateIntermediatePoints(lastPoint!!,
                currentPoint!!, maxDistance, errorVariance)
            if (im.isEmpty()) {
                lastPoint = currentPoint
            } else {
                intermediatePoints.addAll(im)
                intermediatePoints.add(currentPoint!!)
                lastPoint = intermediatePoints.removeAt(0)
            }
        } else {
            lastPoint = currentPoint
        }

        return lastPoint
    }

    fun getTrackNames(): List<String> {
        return tracks.map { it.name ?: "Unnamed Track" }
    }

    fun deepCopy(): SimTrack {
        return SimTrack(
            tracks = tracks.map { it.deepCopy() }.toMutableList(),
            currentPointIndex = currentPointIndex,
            selectedTrackIndex = selectedTrackIndex,
            selectedMode = selectedMode,
            maxDistance = maxDistance,
            errorVariance = errorVariance,
            fileName = fileName,
            currentTrack = currentTrack?.deepCopy(),
            lastPoint = lastPoint?.copy(),
            intermediatePoints = intermediatePoints.map { it.copy() }.toMutableList()
        )
    }

    fun updatePIndex(newIndex: Int) {
        val currentTrack = tracks.getOrNull(selectedTrackIndex)
        val maxIndex = currentTrack?.points?.size ?: 0

        when {
            newIndex < 0 -> {
                currentPointIndex = 0
            }
            newIndex >= maxIndex -> {
                currentPointIndex = maxIndex - 1
            }
            else -> {
                currentPointIndex = newIndex
            }
        }

        // Zurücksetzen der Zwischenpunkte und des letzten Punktes
        intermediatePoints.clear()
        lastPoint = null
    }


    fun calculateIntermediatePoints(
        startPoint: TrackPoint, endPoint: TrackPoint,
        maxDistance: Double, error: Double = 0.0
    ): List<TrackPoint> {
        if (maxDistance == 0.0) {
            return emptyList()
        }
        val distance = startPoint.calculateDistance(endPoint)
        if (distance <= maxDistance) {
            return emptyList()
        }
        val numPoints = (distance / maxDistance).toInt()
        val points = mutableListOf<TrackPoint>()
        val (dNs, dOw) = startPoint.calculateVector(endPoint)
        val dAlt = ((endPoint.elevation ?: 0.0) - (startPoint.elevation ?: 0.0)) / (numPoints + 1)
        val dNsStep = dNs / (numPoints + 1)
        val dOwStep = dOw / (numPoints + 1)
        for (i in 1..numPoints) {
            val errLat = if (error != 0.0) Random.nextDouble(-error, error) else 0.0
            val errLong = if (error != 0.0) Random.nextDouble(-error, error) else 0.0
            val errAlt = if (error != 0.0) Random.nextDouble(-error, error) else 0.0
            val (iLat, iLong, iAlt) = startPoint.calcNewTrackPoint(
                i * dNsStep + errLat, i * dOwStep + errLong, i * dAlt + errAlt
            )
            points.add(TrackPoint(iLat, iLong, iAlt))
        }
        return points
    }
}

fun speed2distance(speedKmh: Double, timeSec: Double): Double {
    val speedMs = speedKmh * (1000 / 3600.0)
    return speedMs * timeSec
}

@Parcelize
class Track(var name: String? = null, val points: MutableList<TrackPoint> = mutableListOf() ) : Parcelable {
    fun addPoint(point: TrackPoint) {
        points.add(point)
    }
    fun deepCopy(): Track {
        return Track(name, points.map { it.copy() }.toMutableList())
    }
}

@Parcelize
data class TrackPoint(val latitude: Double, val longitude: Double, val elevation: Double?) : Parcelable {

    fun calculateDistance(other: TrackPoint): Double {
        val lat1Rad = Math.toRadians(this.latitude)
        val long1Rad = Math.toRadians(this.longitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val long2Rad = Math.toRadians(other.longitude)

        val dlat = lat2Rad - lat1Rad
        val dlong = long2Rad - long1Rad
        val a = sin(dlat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dlong / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val R = 6378137.0
        val surfaceDistance = R * c

        val dalt = (other.elevation ?: 0.0) - (this.elevation ?: 0.0)

        return sqrt(surfaceDistance.pow(2) + dalt.pow(2))
    }

    fun calcNewTrackPoint(NS: Double, OW: Double, deltaAlt: Double): TrackPoint {
        val R = 6378137.0
        val latRad = Math.toRadians(this.latitude)
        val longRad = Math.toRadians(this.longitude)
        val deltaLat = NS / R
        val newLatRad = latRad + deltaLat
        val newLat = Math.toDegrees(newLatRad)
        val deltaLong = OW / (R * cos(latRad))
        val newLongRad = longRad + deltaLong
        val newLong = Math.toDegrees(newLongRad)
        val newAlt = this.elevation?.plus(deltaAlt)
        return TrackPoint(newLat, newLong, newAlt)
    }

    fun calculateVector(other: TrackPoint): Pair<Double, Double> {
        val R = 6378137.0
        val lat1Rad = Math.toRadians(this.latitude)
        val long1Rad = Math.toRadians(this.longitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val long2Rad = Math.toRadians(other.longitude)
        val dlat = lat2Rad - lat1Rad
        val dlong = long2Rad - long1Rad
        val NS = dlat * R
        val OW = dlong * R * cos((lat1Rad + lat2Rad) / 2)
        return Pair(NS, OW)
    }
}