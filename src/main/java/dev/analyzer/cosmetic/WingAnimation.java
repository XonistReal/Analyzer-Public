package dev.analyzer.cosmetic;

import java.util.ArrayList;
import java.util.List;

/** Deterministic render-only port of the original Wings rest/idle/lift/glide poses.
 * Copyright (c) 2018 Paul Fulham. MIT; see WINGS-MIT-LICENSE.txt.
 * No flight controller, packets, player angles or physical entity state. */
public final class WingAnimation {
    public enum Mode { AUTO, FLAP, IDLE, GLIDE, STILL }
    public enum Motion { IDLE, FLAP, GLIDE, STILL }
    public record Rotation(double x, double y, double z) { }
    public record Pose(Motion motion, List<Rotation> bones, List<Rotation> feathers) {
        public Pose { bones = List.copyOf(bones); feathers = List.copyOf(feathers); }
    }
    private static final List<Rotation> REST = List.of(new Rotation(0,-23.5,-16), new Rotation(0,13,29),
            new Rotation(0,12,-28), new Rotation(0,4,18.3));
    private static final List<Rotation> REST_FEATHERS = List.of(new Rotation(0,0,23.48), new Rotation(0,0,0),
            new Rotation(0,0,0), new Rotation(0,0,0));
    private static final List<Rotation> IDLE = List.of(new Rotation(40,-60,-50), new Rotation(72,10,100),
            new Rotation(0,-10,-120), new Rotation(10,0,100));
    private static final List<Rotation> IDLE_FEATHERS = List.of(new Rotation(10,20,23.48), new Rotation(0,20,-70),
            new Rotation(0,10,40), new Rotation(-20,0,20));
    private WingAnimation() { }
    public static Pose pose(Mode mode, double age, double speed, double strength, boolean gliding, boolean swimming, boolean airborne) {
        speed = finiteClamp(speed,0,3,1); strength = finiteClamp(strength,0,1.5,1);
        age = Double.isFinite(age) ? age : 0;
        Motion motion = switch (mode == null ? Mode.AUTO : mode) {
            case AUTO -> gliding || swimming ? Motion.GLIDE : airborne ? Motion.FLAP : Motion.IDLE;
            case FLAP -> Motion.FLAP; case GLIDE -> Motion.GLIDE; case IDLE -> Motion.IDLE; case STILL -> Motion.STILL;
        };
        double rate = switch (motion) { case FLAP -> .3; case GLIDE -> .045; case IDLE -> .035; default -> 0; };
        double time = (age * speed * rate) % (2 * Math.PI);
        if (!Double.isFinite(time)) time = 0;
        var bones = new ArrayList<Rotation>(4); var feathers = new ArrayList<Rotation>(4);
        for (int i = 0; i < 4; i++) {
            double weight = Math.min(Math.abs(i - 1),2) / 2.0;
            Rotation base = REST.get(i), feather = REST_FEATHERS.get(i), target = base, targetFeather = feather;
            if (motion == Motion.IDLE) {
                Rotation idle = IDLE.get(i), idleFeather = IDLE_FEATHERS.get(i);
                target = new Rotation(idle.x(),idle.y() + Math.sin(time) * 3 * weight,idle.z());
                targetFeather = new Rotation(idleFeather.x(), idleFeather.y() - Math.sin(time) * 5 * weight,idleFeather.z());
            } else if (motion == Motion.FLAP) {
                double cycle = time - weight * 1.2;
                double x = (Math.sin(cycle + Math.PI/2) - 1)/2 * 16 + 8;
                double y = (Math.sin(cycle)*26 + 12) * (1 - weight * (Math.min(Math.sin(cycle + Math.PI),0)/2 + 1) * Math.sin(time));
                target = new Rotation(base.x()+x,base.y()+y,base.z());
            } else if (motion == Motion.GLIDE) {
                target = new Rotation(base.x(),base.y() + (Math.sin(time)*5-14)*weight,base.z());
                // Deterministic low-amplitude flutter replaces the old randomly seeded noise generator.
                targetFeather = new Rotation(feather.x()+Math.sin(time*3+i*.7)*1.25,feather.y(),feather.z());
            }
            bones.add(lerp(base,target,strength)); feathers.add(lerp(feather,targetFeather,strength));
        }
        return new Pose(motion,bones,feathers);
    }
    public static Rotation mirror(Rotation rotation, boolean right) {
        return right ? new Rotation(rotation.x(),-rotation.y(),-rotation.z()) : rotation;
    }
    /** Positive user height raises the already approved upper-back mount. Units: Minecraft pixels. */
    public static double mountY(double heightPixels) { return (-3 - finiteClamp(heightPixels,-4,8,0)) / 16; }
    public static double mountZ(double backPixels, boolean armorClearance, boolean armorEquipped) {
        return (finiteClamp(backPixels,-2,6,0) + (armorClearance && armorEquipped ? .8 : 0)) / 16;
    }
    private static Rotation lerp(Rotation a, Rotation b, double t) {
        return new Rotation(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);
    }
    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min,Math.min(max,value)) : fallback;
    }
}
