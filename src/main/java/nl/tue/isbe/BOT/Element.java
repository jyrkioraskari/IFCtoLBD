package nl.tue.isbe.BOT;

/*
 *
 * Copyright 2019 Pieter Pauwels, Eindhoven University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.IFC.IfcElementType;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Element {

    private Guid guid = new Guid();
    private String ifcName;
    private String className;
    private String name;
    private long lineNum;

    private String label = "";
    private String description = "";
    private String tag = "";
    private String namespace;

    private IfcElementType ifcElementType;

    private Space boundingSpace;
    private Storey containingStorey;
    private Space containingSpace;

    private Element partOfElement;
    private Element hostingElement;

    private List<Property> properties = new ArrayList<Property>();

    private List<Element> subElements = new ArrayList<Element>(); //hasSubElement //Relation between an element a) and another element b) hosted by element a)
    private List<Element> hostedElements = new ArrayList<Element>();

    private IFCVO lineEntry;
    public static List<Element> elementList = new ArrayList<Element>();

    public enum BEO_classes {Beam, Beam__BEAM, Beam__HOLLOWCORE, Beam__JOIST, Beam__LINTEL, Beam__SPANDREL, Beam__T_BEAM, Chimney, Column,
        Column__COLUMN, Column__PILASTER, Covering, Covering__CEILING, Covering__CLADDING, Covering__FLOORING, Covering__INSULATION,
        Covering__MEMBRANE, Covering__MOLDING, Covering__ROOFING, Covering__SKIRTINGBOARD, Covering__SLEEVING, Covering__WRAPPING,
        CurtainWall, Door, Door__DOOR, Door__GATE, Door__TRAPDOOR, Footing, Footing__CAISSON_FOUNDATION, Footing__FOOTING_BEAM,
        Footing__PAD_FOOTING, Footing__PILE_CAP, Footing__STRIP_FOOTING, Member, Member__BRACE, Member__CHORD, Member__COLLAR,
        Member__MEMBER, Member__MULLION, Member__PLATE, Member__POST, Member__PURLIN, Member__RAFTER, Member__STRINGER, Member__STRUT,
        Member__STUD, Pile, Pile__BORED, Pile__COHESION, Pile__DRIVEN, Pile__FRICTION, Pile__JETGROUTING, Pile__SUPPORT, Plate,
        Plate__CURTAIN_PANEL, Plate__SHEET, Railing, Railing__BALUSTRADE, Railing__GUARDRAIL, Railing__HANDRAIL, Ramp, Ramp__HALF_TURN_RAMP,
        Ramp__QUARTER_TURN_RAMP, Ramp__SPIRAL_RAMP, Ramp__STRAIGHT_RUN_RAMP, Ramp__TWO_QUARTER_TURN_RAMP, Ramp__TWO_STRAIGHT_RUN_RAMP,
        RampFlight, RampFlight__SPIRAL, RampFlight__STRAIGHT, Roof, Roof__BARREL_ROOF, Roof__BUTTERFLY_ROOF, Roof__DOME_ROOF,
        Roof__FLAT_ROOF, Roof__FREEFORM, Roof__GABLE_ROOF, Roof__GAMBREL_ROOF, Roof__HIPPED_GABLE_ROOF, Roof__HIP_ROOF, Roof__MANSARD_ROOF,
        Roof__PAVILION_ROOF, Roof__RAINBOW_ROOF, Roof__SHED_ROOF, ShadingDevice, ShadingDevice__AWNING, ShadingDevice__JALOUSIE,
        ShadingDevice__SHUTTER, Slab, Slab__BASESLAB, Slab__FLOOR, Slab__LANDING, Slab__ROOF, Stair, Stair__CURVED_RUN_STAIR,
        Stair__DOUBLE_RETURN_STAIR, Stair__HALF_TURN_STAIR, Stair__HALF_WINDING_STAIR, Stair__QUARTER_TURN_STAIR, Stair__QUARTER_WINDING_STAIR,
        Stair__SPIRAL_STAIR, Stair__STRAIGHT_RUN_STAIR, Stair__THREE_QUARTER_TURN_STAIR, Stair__THREE_QUARTER_WINDING_STAIR,
        Stair__TWO_CURVED_RUN_STAIR, Stair__TWO_QUARTER_TURN_STAIR, Stair__TWO_QUARTER_WINDING_STAIR, Stair__TWO_STRAIGHT_RUN_STAIR,
        StairFlight, StairFlight__CURVED, StairFlight__FREEFORM, StairFlight__SPIRAL, StairFlight__STRAIGHT, StairFlight__WINDER,
        Wall, Wall__ELEMENTEDWALL, Wall__MOVABLE, Wall__PARAPET, Wall__PARTITIONING, Wall__PLUMBINGWALL, Wall__POLYGONAL, Wall__SHEAR,
        Wall__SOLIDWALL, Wall__STANDARD, WallElementedCase, Window, Window__LIGHTDOME, Window__SKYLIGHT, Window__WINDOW};

    public enum ifc4_add2_tc1_BEO_classes {IfcPile, IfcCovering, IfcStairFlight, IfcDoor, IfcWindow, IfcBeam, IfcBuildingElementProxy,
        IfcChimney, IfcColumn, IfcCurtainWall, IfcFooting, IfcMember, IfcPlate, IfcRailing, IfcRamp, IfcRampFlight, IfcRoof,
        IfcShadingDevice, IfcSlab, IfcStair, IfcWall, IfcBeamStandardCase, IfcColumnStandardCase, IfcDoorStandardCase, IfcMemberStandardCase,
        IfcPlateStandardCase, IfcSlabElementedCase, IfcSlabStandardCase, IfcWallElementedCase, IfcWallStandardCase, IfcWindowStandardCase};
    public enum ifc2x3_tc1_BEO_classes {IfcSlab, IfcTendonAnchor, IfcReinforcingElement, IfcReinforcingBar, IfcReinforcingMesh, IfcTendon,
        IfcBeam, IfcStair, IfcRamp, IfcPlate, IfcWall, IfcFooting, IfcStairFlight, IfcRailing, IfcPile, IfcColumn, IfcRoof, IfcWindow,
        IfcCurtainWall, IfcMember, IfcBuildingElementComponent, IfcDoor, IfcCovering, IfcBuildingElementProxy, IfcRampFlight,
        IfcBuildingElementPart, IfcWallStandardCase};
    public enum MEP_classes {Actuator, DistributionControlElement, Actuator__ELECTRICACTUATOR, Actuator__HANDOPERATEDACTUATOR,
        Actuator__HYDRAULICACTUATOR, Actuator__PNEUMATICACTUATOR, Actuator__THERMOSTATICACTUATOR, AirTerminal, FlowTerminal,
        AirTerminal__DIFFUSER, AirTerminal__GRILLE, AirTerminal__LOUVRE, AirTerminal__REGISTER, AirTerminalBox, FlowController,
        AirTerminalBox__CONSTANTFLOW, AirTerminalBox__VARIABLEFLOWPRESSUREDEPENDANT, AirTerminalBox__VARIABLEFLOWPRESSUREINDEPENDANT,
        AirToAirHeatRecovery, EnergyConversionDevice, AirToAirHeatRecovery__FIXEDPLATECOUNTERFLOWEXCHANGER, AirToAirHeatRecovery__FIXEDPLATECROSSFLOWEXCHANGER,
        AirToAirHeatRecovery__FIXEDPLATEPARALLELFLOWEXCHANGER, AirToAirHeatRecovery__HEATPIPE, AirToAirHeatRecovery__ROTARYWHEEL,
        AirToAirHeatRecovery__RUNAROUNDCOILLOOP, AirToAirHeatRecovery__THERMOSIPHONCOILTYPEHEATEXCHANGERS, AirToAirHeatRecovery__THERMOSIPHONSEALEDTUBEHEATEXCHANGERS,
        AirToAirHeatRecovery__TWINTOWERENTHALPYRECOVERYLOOPS, Alarm, Alarm__BELL, Alarm__BREAKGLASSBUTTON, Alarm__LIGHT,
        Alarm__MANUALPULLBOX, Alarm__SIREN, Alarm__WHISTLE, AudioVisualAppliance, AudioVisualAppliance__AMPLIFIER, AudioVisualAppliance__CAMERA,
        AudioVisualAppliance__DISPLAY, AudioVisualAppliance__MICROPHONE, AudioVisualAppliance__PLAYER, AudioVisualAppliance__PROJECTOR,
        AudioVisualAppliance__RECEIVER, AudioVisualAppliance__SPEAKER, AudioVisualAppliance__SWITCHER, AudioVisualAppliance__TELEPHONE,
        AudioVisualAppliance__TUNER, Boiler, Boiler__STEAM, Boiler__WATER, Burner, CableCarrierFitting, FlowFitting, CableCarrierFitting__BEND,
        CableCarrierFitting__CROSS, CableCarrierFitting__REDUCER, CableCarrierFitting__TEE, CableCarrierSegment, FlowSegment,
        CableCarrierSegment__CABLELADDERSEGMENT, CableCarrierSegment__CABLETRAYSEGMENT, CableCarrierSegment__CABLETRUNKINGSEGMENT,
        CableCarrierSegment__CONDUITSEGMENT, CableFitting, CableFitting__CONNECTOR, CableFitting__ENTRY, CableFitting__EXIT,
        CableFitting__JUNCTION, CableFitting__TRANSITION, CableSegment, CableSegment__BUSBARSEGMENT, CableSegment__CONDUCTORSEGMENT,
        CableSegment__CORESEGMENT, Chiller, Chiller__AIRCOOLED, Chiller__HEATRECOVERY, Chiller__WATERCOOLED, Coil, Coil__DXCOOLINGCOIL,
        Coil__ELECTRICHEATINGCOIL, Coil__GASHEATINGCOIL, Coil__HYDRONICCOIL, Coil__STEAMHEATINGCOIL, Coil__WATERCOOLINGCOIL, Coil__WATERHEATINGCOIL,
        CommunicationsAppliance, CommunicationsAppliance__ANTENNA, CommunicationsAppliance__COMPUTER, CommunicationsAppliance__FAX,
        CommunicationsAppliance__GATEWAY, CommunicationsAppliance__MODEM, CommunicationsAppliance__NETWORKAPPLIANCE, CommunicationsAppliance__NETWORKBRIDGE,
        CommunicationsAppliance__NETWORKHUB, CommunicationsAppliance__PRINTER, CommunicationsAppliance__REPEATER, CommunicationsAppliance__ROUTER,
        CommunicationsAppliance__SCANNER, Compressor, FlowMovingDevice, Compressor__BOOSTER, Compressor__DYNAMIC, Compressor__HERMETIC,
        Compressor__OPENTYPE, Compressor__RECIPROCATING, Compressor__ROLLINGPISTON, Compressor__ROTARY, Compressor__ROTARYVANE,
        Compressor__SCROLL, Compressor__SEMIHERMETIC, Compressor__SINGLESCREW, Compressor__SINGLESTAGE, Compressor__TROCHOIDAL,
        Compressor__TWINSCREW, Compressor__WELDEDSHELLHERMETIC, Condenser, Condenser__AIRCOOLED, Condenser__EVAPORATIVECOOLED,
        Condenser__WATERCOOLED, Condenser__WATERCOOLEDBRAZEDPLATE, Condenser__WATERCOOLEDSHELLCOIL, Condenser__WATERCOOLEDSHELLTUBE,
        Condenser__WATERCOOLEDTUBEINTUBE, Controller, Controller__FLOATING, Controller__MULTIPOSITION, Controller__PROGRAMMABLE,
        Controller__PROPORTIONAL, Controller__TWOPOSITION, CooledBeam, CooledBeam__ACTIVE, CooledBeam__PASSIVE, CoolingTower,
        CoolingTower__MECHANICALFORCEDDRAFT, CoolingTower__MECHANICALINDUCEDDRAFT, CoolingTower__NATURALDRAFT, Damper, Damper__BACKDRAFTDAMPER,
        Damper__BALANCINGDAMPER, Damper__BLASTDAMPER, Damper__CONTROLDAMPER, Damper__FIREDAMPER, Damper__FIRESMOKEDAMPER, Damper__FUMEHOODEXHAUST,
        Damper__GRAVITYDAMPER, Damper__GRAVITYRELIEFDAMPER, Damper__RELIEFDAMPER, Damper__SMOKEDAMPER, DistributionChamberElement,
        DistributionFlowElement, DistributionChamberElement__FORMEDDUCT, DistributionChamberElement__INSPECTIONCHAMBER,
        DistributionChamberElement__INSPECTIONPIT, DistributionChamberElement__MANHOLE, DistributionChamberElement__METERCHAMBER,
        DistributionChamberElement__SUMP, DistributionChamberElement__TRENCH, DistributionChamberElement__VALVECHAMBER, DuctFitting,
        DuctFitting__BEND, DuctFitting__CONNECTOR, DuctFitting__ENTRY, DuctFitting__EXIT, DuctFitting__JUNCTION, DuctFitting__OBSTRUCTION, DuctFitting__TRANSITION,
        DuctSegment, DuctSegment__FLEXIBLESEGMENT, DuctSegment__RIGIDSEGMENT, DuctSilencer, FlowTreatmentDevice, DuctSilencer__FLATOVAL,
        DuctSilencer__RECTANGULAR, DuctSilencer__ROUND, ElectricAppliance, ElectricAppliance__DISHWASHER, ElectricAppliance__ELECTRICCOOKER,
        ElectricAppliance__FREESTANDINGELECTRICHEATER, ElectricAppliance__FREESTANDINGFAN, ElectricAppliance__FREESTANDINGWATERCOOLER,
        ElectricAppliance__FREESTANDINGWATERHEATER, ElectricAppliance__FREEZER, ElectricAppliance__FRIDGE_FREEZER, ElectricAppliance__HANDDRYER,
        ElectricAppliance__KITCHENMACHINE, ElectricAppliance__MICROWAVE, ElectricAppliance__PHOTOCOPIER, ElectricAppliance__REFRIGERATOR,
        ElectricAppliance__TUMBLEDRYER, ElectricAppliance__VENDINGMACHINE, ElectricAppliance__WASHINGMACHINE, ElectricDistributionBoard,
        ElectricDistributionBoard__CONSUMERUNIT, ElectricDistributionBoard__DISTRIBUTIONBOARD, ElectricDistributionBoard__MOTORCONTROLCENTRE,
        ElectricDistributionBoard__SWITCHBOARD, ElectricFlowStorageDevice, FlowStorageDevice, ElectricFlowStorageDevice__BATTERY,
        ElectricFlowStorageDevice__CAPACITORBANK, ElectricFlowStorageDevice__HARMONICFILTER, ElectricFlowStorageDevice__INDUCTORBANK,
        ElectricFlowStorageDevice__UPS, ElectricGenerator, ElectricGenerator__CHP, ElectricGenerator__ENGINEGENERATOR, ElectricGenerator__STANDALONE,
        ElectricMotor, ElectricMotor__DC, ElectricMotor__INDUCTION, ElectricMotor__POLYPHASE, ElectricMotor__RELUCTANCESYNCHRONOUS,
        ElectricMotor__SYNCHRONOUS, ElectricTimeControl, ElectricTimeControl__RELAY, ElectricTimeControl__TIMECLOCK, ElectricTimeControl__TIMEDELAY,
        Engine, Engine__EXTERNALCOMBUSTION, Engine__INTERNALCOMBUSTION, EvaporativeCooler, EvaporativeCooler__DIRECTEVAPORATIVEAIRWASHER,
        EvaporativeCooler__DIRECTEVAPORATIVEPACKAGEDROTARYAIRCOOLER, EvaporativeCooler__DIRECTEVAPORATIVERANDOMMEDIAAIRCOOLER,
        EvaporativeCooler__DIRECTEVAPORATIVERIGIDMEDIAAIRCOOLER, EvaporativeCooler__DIRECTEVAPORATIVESLINGERSPACKAGEDAIRCOOLER,
        EvaporativeCooler__INDIRECTDIRECTCOMBINATION, EvaporativeCooler__INDIRECTEVAPORATIVECOOLINGTOWERORCOILCOOLER, EvaporativeCooler__INDIRECTEVAPORATIVEPACKAGEAIRCOOLER,
        EvaporativeCooler__INDIRECTEVAPORATIVEWETCOIL, Evaporator, Evaporator__DIRECTEXPANSION, Evaporator__DIRECTEXPANSIONBRAZEDPLATE,
        Evaporator__DIRECTEXPANSIONSHELLANDTUBE, Evaporator__DIRECTEXPANSIONTUBEINTUBE, Evaporator__FLOODEDSHELLANDTUBE, Evaporator__SHELLANDCOIL,
        Fan, Fan__CENTRIFUGALAIRFOIL, Fan__CENTRIFUGALBACKWARDINCLINEDCURVED, Fan__CENTRIFUGALFORWARDCURVED, Fan__CENTRIFUGALRADIAL,
        Fan__PROPELLORAXIAL, Fan__TUBEAXIAL, Fan__VANEAXIAL, Filter, Filter__AIRPARTICLEFILTER, Filter__COMPRESSEDAIRFILTER, Filter__ODORFILTER,
        Filter__OILFILTER, Filter__STRAINER, Filter__WATERFILTER, FireSuppressionTerminal, FireSuppressionTerminal__BREECHINGINLET,
        FireSuppressionTerminal__FIREHYDRANT, FireSuppressionTerminal__HOSEREEL, FireSuppressionTerminal__SPRINKLER, FireSuppressionTerminal__SPRINKLERDEFLECTOR,
        FlowInstrument, FlowInstrument__AMMETER, FlowInstrument__FREQUENCYMETER, FlowInstrument__PHASEANGLEMETER, FlowInstrument__POWERFACTORMETER,
        FlowInstrument__PRESSUREGAUGE, FlowInstrument__THERMOMETER, FlowInstrument__VOLTMETER_PEAK, FlowInstrument__VOLTMETER_RMS, FlowMeter,
        FlowMeter__ENERGYMETER, FlowMeter__GASMETER, FlowMeter__OILMETER, FlowMeter__WATERMETER, HeatExchanger, HeatExchanger__PLATE,
        HeatExchanger__SHELLANDTUBE, Humidifier, Humidifier__ADIABATICAIRWASHER, Humidifier__ADIABATICATOMIZING, Humidifier__ADIABATICCOMPRESSEDAIRNOZZLE,
        Humidifier__ADIABATICPAN, Humidifier__ADIABATICRIGIDMEDIA, Humidifier__ADIABATICULTRASONIC, Humidifier__ADIABATICWETTEDELEMENT,
        Humidifier__ASSISTEDBUTANE, Humidifier__ASSISTEDELECTRIC, Humidifier__ASSISTEDNATURALGAS, Humidifier__ASSISTEDPROPANE,
        Humidifier__ASSISTEDSTEAM, Humidifier__STEAMINJECTION, Interceptor, Interceptor__CYCLONIC, Interceptor__GREASE, Interceptor__OIL,
        Interceptor__PETROL, JunctionBox, JunctionBox__DATA, JunctionBox__POWER, Lamp, Lamp__COMPACTFLUORESCENT, Lamp__FLUORESCENT,
        Lamp__HALOGEN, Lamp__HIGHPRESSUREMERCURY, Lamp__HIGHPRESSURESODIUM, Lamp__LED, Lamp__METALHALIDE, Lamp__OLED, Lamp__TUNGSTENFILAMENT,
        LightFixture, LightFixture__DIRECTIONSOURCE, LightFixture__POINTSOURCE, LightFixture__SECURITYLIGHTING, MedicalDevice, MedicalDevice__AIRSTATION,
        MedicalDevice__FEEDAIRUNIT, MedicalDevice__OXYGENGENERATOR, MedicalDevice__OXYGENPLANT, MedicalDevice__VACUUMSTATION, MotorConnection,
        MotorConnection__BELTDRIVE, MotorConnection__COUPLING, MotorConnection__DIRECTDRIVE, Outlet, Outlet__AUDIOVISUALOUTLET, Outlet__COMMUNICATIONSOUTLET,
        Outlet__DATAOUTLET, Outlet__POWEROUTLET, Outlet__TELEPHONEOUTLET, PipeFitting, PipeFitting__BEND, PipeFitting__CONNECTOR, PipeFitting__ENTRY,
        PipeFitting__EXIT, PipeFitting__JUNCTION, PipeFitting__OBSTRUCTION, PipeFitting__TRANSITION, PipeSegment, PipeSegment__CULVERT,
        PipeSegment__FLEXIBLESEGMENT, PipeSegment__GUTTER, PipeSegment__RIGIDSEGMENT, PipeSegment__SPOOL, ProtectiveDevice, ProtectiveDevice__CIRCUITBREAKER,
        ProtectiveDevice__EARTHINGSWITCH, ProtectiveDevice__EARTHLEAKAGECIRCUITBREAKER, ProtectiveDevice__FUSEDISCONNECTOR, ProtectiveDevice__RESIDUALCURRENTCIRCUITBREAKER,
        ProtectiveDevice__RESIDUALCURRENTSWITCH, ProtectiveDevice__VARISTOR, ProtectiveDeviceTrippingUnit, ProtectiveDeviceTrippingUnit__ELECTROMAGNETIC,
        ProtectiveDeviceTrippingUnit__ELECTRONIC, ProtectiveDeviceTrippingUnit__RESIDUALCURRENT, ProtectiveDeviceTrippingUnit__THERMAL,
        Pump, Pump__CIRCULATOR, Pump__ENDSUCTION, Pump__SPLITCASE, Pump__SUBMERSIBLEPUMP, Pump__SUMPPUMP, Pump__VERTICALINLINE,
        Pump__VERTICALTURBINE, SanitaryTerminal, SanitaryTerminal__BATH, SanitaryTerminal__BIDET, SanitaryTerminal__CISTERN,
        SanitaryTerminal__SANITARYFOUNTAIN, SanitaryTerminal__SHOWER, SanitaryTerminal__SINK, SanitaryTerminal__TOILETPAN,
        SanitaryTerminal__URINAL, SanitaryTerminal__WASHHANDBASIN, SanitaryTerminal__WCSEAT, Sensor, Sensor__CO2SENSOR, Sensor__CONDUCTANCESENSOR,
        Sensor__CONTACTSENSOR, Sensor__FIRESENSOR, Sensor__FLOWSENSOR, Sensor__FROSTSENSOR, Sensor__GASSENSOR, Sensor__HEATSENSOR, Sensor__HUMIDITYSENSOR,
        Sensor__IDENTIFIERSENSOR, Sensor__IONCONCENTRATIONSENSOR, Sensor__LEVELSENSOR, Sensor__LIGHTSENSOR, Sensor__MOISTURESENSOR,
        Sensor__MOVEMENTSENSOR, Sensor__PHSENSOR, Sensor__PRESSURESENSOR, Sensor__RADIATIONSENSOR, Sensor__RADIOACTIVITYSENSOR,
        Sensor__SMOKESENSOR, Sensor__SOUNDSENSOR, Sensor__TEMPERATURESENSOR, Sensor__WINDSENSOR, SolarDevice, SolarDevice__SOLARCOLLECTOR,
        SolarDevice__SOLARPANEL, SpaceHeater, SpaceHeater__CONVECTOR, SpaceHeater__RADIATOR, StackTerminal, StackTerminal__BIRDCAGE,
        StackTerminal__COWL, StackTerminal__RAINWATERHOPPER, SwitchingDevice, SwitchingDevice__CONTACTOR, SwitchingDevice__DIMMERSWITCH,
        SwitchingDevice__EMERGENCYSTOP, SwitchingDevice__KEYPAD, SwitchingDevice__MOMENTARYSWITCH, SwitchingDevice__SELECTORSWITCH,
        SwitchingDevice__STARTER, SwitchingDevice__SWITCHDISCONNECTOR, SwitchingDevice__TOGGLESWITCH, Tank, Tank__BASIN, Tank__BREAKPRESSURE,
        Tank__EXPANSION, Tank__FEEDANDEXPANSION, Tank__PRESSUREVESSEL, Tank__STORAGE, Tank__VESSEL, Transformer, Transformer__CURRENT,
        Transformer__FREQUENCY, Transformer__INVERTER, Transformer__RECTIFIER, Transformer__VOLTAGE, TubeBundle, TubeBundle__FINNED,
        UnitaryControlElement, UnitaryControlElement__ALARMPANEL, UnitaryControlElement__CONTROLPANEL, UnitaryControlElement__GASDETECTIONPANEL,
        UnitaryControlElement__HUMIDISTAT, UnitaryControlElement__INDICATORPANEL, UnitaryControlElement__MIMICPANEL, UnitaryControlElement__THERMOSTAT,
        UnitaryControlElement__WEATHERSTATION, UnitaryEquipment, UnitaryEquipment__AIRCONDITIONINGUNIT, UnitaryEquipment__AIRHANDLER,
        UnitaryEquipment__DEHUMIDIFIER, UnitaryEquipment__ROOFTOPUNIT, UnitaryEquipment__SPLITSYSTEM, Valve, Valve__AIRRELEASE, Valve__ANTIVACUUM,
        Valve__CHANGEOVER, Valve__CHECK, Valve__COMMISSIONING, Valve__DIVERTING, Valve__DOUBLECHECK, Valve__DOUBLEREGULATING, Valve__DRAWOFFCOCK,
        Valve__FAUCET, Valve__FLUSHING, Valve__GASCOCK, Valve__GASTAP, Valve__ISOLATING, Valve__MIXING, Valve__PRESSUREREDUCING, Valve__PRESSURERELIEF,
        Valve__REGULATING, Valve__SAFETYCUTOFF, Valve__STEAMTRAP, Valve__STOPCOCK, WasteTerminal, WasteTerminal__FLOORTRAP, WasteTerminal__FLOORWASTE,
        WasteTerminal__GULLYSUMP, WasteTerminal__GULLYTRAP, WasteTerminal__ROOFDRAIN, WasteTerminal__WASTEDISPOSALUNIT, WasteTerminal__WASTETRAP};
    public enum ifc4_add2_tc1_MEP_classes {IfcDistributionControlElement, IfcDistributionFlowElement, IfcActuator, IfcAirTerminal,
        IfcAirTerminalBox, IfcAirToAirHeatRecovery, IfcAlarm, IfcAudioVisualAppliance, IfcBoiler, IfcBurner, IfcCableCarrierFitting,
        IfcCableCarrierSegment, IfcCableFitting, IfcCableSegment, IfcChiller, IfcCoil, IfcCommunicationsAppliance, IfcCompressor,
        IfcCondenser, IfcController, IfcCooledBeam, IfcCoolingTower, IfcDamper, IfcDistributionChamberElement, IfcDuctFitting,
        IfcDuctSegment, IfcDuctSilencer, IfcElectricAppliance, IfcElectricDistributionBoard, IfcElectricFlowStorageDevice,
        IfcElectricGenerator, IfcElectricMotor, IfcElectricTimeControl, IfcEngine, IfcEvaporativeCooler, IfcEvaporator,
        IfcFan, IfcFilter, IfcFireSuppressionTerminal, IfcFlowInstrument, IfcFlowMeter, IfcHeatExchanger, IfcHumidifier, IfcInterceptor,
        IfcJunctionBox, IfcLamp, IfcLightFixture, IfcMedicalDevice, IfcMotorConnection, IfcOutlet, IfcPipeFitting, IfcPipeSegment,
        IfcProtectiveDevice, IfcProtectiveDeviceTrippingUnit, IfcPump, IfcSanitaryTerminal, IfcSensor, IfcSolarDevice, IfcSpaceHeater,
        IfcStackTerminal, IfcSwitchingDevice, IfcTank, IfcTransformer, IfcTubeBundle, IfcUnitaryControlElement, IfcUnitaryEquipment,
        IfcValve, IfcWasteTerminal, IfcFlowTerminal, IfcFlowController, IfcEnergyConversionDevice, IfcFlowFitting, IfcFlowSegment,
        IfcFlowMovingDevice, IfcFlowStorageDevice, IfcFlowTreatmentDevice};
    public enum ifc2x3_tc1_MEP_classes {IfcElectricDistributionPoint, IfcFlowTerminal, IfcDistributionFlowElement, IfcFlowTreatmentDevice,
        IfcEnergyConversionDevice, IfcDistributionChamberElement, IfcFlowStorageDevice, IfcFlowSegment, IfcFlowController, IfcFlowFitting,
        IfcFlowMovingDevice, IfcDistributionControlElement};

    public Element(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        elementList.add(this);
        getCorrectNameBasedOnIFCInput(); //set the name once based on IFC input
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
        tag = ((String) lineEntry.getObjectList().get(14)).substring(1);
    }


    //------------
    // ACCESSORS
    //------------

    public Guid getGuid() {
        return guid;
    }

    public long getLineNum() {
        return lineNum;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public String getNamespace(){ return namespace; }

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcElementType getIfcElementType() {
        return ifcElementType;
    }

    public void setIfcElementType(IfcElementType iet){
        this.ifcElementType = iet;
    }

    public void setContainingStorey(Storey s){
        containingStorey = s;
    }

    public void setContainingSpace(Space s){
        containingSpace = s;
    }

    public void setBoundingSpace(Space boundingSpace) {
        this.boundingSpace = boundingSpace;
    }

    private void getCorrectNameBasedOnIFCInput(){
        //It has already been checked that these elements are contained in one of the enums with class names
        //System.out.println("Checking Element : " + lineEntry.getName());

        //set IFC name
        for (ifc4_add2_tc1_BEO_classes c : ifc4_add2_tc1_BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(lineEntry.getName())) {
                ifcName = c.name();
                namespace = "beo";
                break;
            }
        }
        if(ifcName==null) {
            for (ifc4_add2_tc1_MEP_classes c : ifc4_add2_tc1_MEP_classes.values()) {
                if (c.name().equalsIgnoreCase(lineEntry.getName())) {
                    ifcName = c.name();
                    namespace = "mep";
                    break;
                }
            }
        }

        //set name
        if(ifcName==null){
            System.out.println("ERROR. No Ifc Name assigned, but that should really be there for : " + lineEntry.getName());
            return;
        }

        //TODO: check for predefinedType

        // map to BEO or MEP ontologies
        String nameNoIFC = ifcName.substring(3);

        //rename standard cases
        if(nameNoIFC.endsWith("StandardCase"))
            nameNoIFC = nameNoIFC.substring(0,nameNoIFC.indexOf("StandardCase"));

        if(nameNoIFC.equalsIgnoreCase("BuildingElementProxy")) {
            className = null;
            name = "element" + "_"+lineEntry.getLineNum();
            return;
        }

        for (BEO_classes c : BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(nameNoIFC)) {
                className = c.name();
                name = toLowerCase(c.name()) + "_"+lineEntry.getLineNum();
                return;
            }
        }
        for (MEP_classes c : MEP_classes.values()) {
            if (c.name().equalsIgnoreCase(nameNoIFC)) {
                className = c.name();
                name = toLowerCase(c.name()) + "_"+lineEntry.getLineNum();
                return;
            }
        }

        System.out.println("ERROR. Element with no name : " + lineEntry.getName());
    }

    public void correctTypeBasedOnIfcType(){
        if(ifcElementType == null)
            return;
        String s = ifcElementType.getPredefinedType();

        if(s==null || s == "")
            return;
        else{
            if(namespace == "beo"){
                for(BEO_classes c : BEO_classes.values()){
                    if(c.name().equalsIgnoreCase(className + "__" + s)) {
                        className = c.name();
                        return;
                    }
                }
            }
            else if (namespace == "mep") {
                for(MEP_classes c : MEP_classes.values()){
                    if(c.name().equalsIgnoreCase(className + "__" + s)) {
                        className = c.name();
                        return;
                    }
                }
            }
            //Sensor__CONDUCTANCESENSOR
        }

    }

    public List<Property> getProperties() {
        return properties;
    }

    public void addProperty(Property p){
        properties.add(p);
    }

    public List<Element> getHostedElements(){
        return hostedElements;
    }

    public void addHostedElement(Element el){
        if(!hostedElements.contains(el))
            hostedElements.add(el);
    }

    public void setHostingElement(Element el){
        hostingElement = el;
    }

    public List<Element> getSubElements(){
        return subElements;
    }

    public void addSubElement(Element el){
        if(!subElements.contains(el))
            subElements.add(el);
    }

    public void setPartOfElement(Element el){
        partOfElement = el;
    }

    public static boolean containedInBEO(String type) {
        for (BEO_classes c : BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containedInMEP(String type) {
        for (MEP_classes c : MEP_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containedInIFC4_ADD2_TC1(String type) {
        for (ifc4_add2_tc1_BEO_classes c : ifc4_add2_tc1_BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        for (ifc4_add2_tc1_MEP_classes c : ifc4_add2_tc1_MEP_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containedInIFC2x3_TC1(String type) {
        for (ifc2x3_tc1_BEO_classes c : ifc2x3_tc1_BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        for (ifc2x3_tc1_MEP_classes c : ifc2x3_tc1_MEP_classes.values()) {
            if (c.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private String toLowerCase(String s){
        char c[] = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return(new String(c));
    }
}
