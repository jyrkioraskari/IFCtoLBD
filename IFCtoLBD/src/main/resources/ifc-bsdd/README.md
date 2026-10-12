# Offline IFC bSDD index

`ifc-4.3-add2.tsv.gz` is a compact runtime index of property-set/quantity-set and
property codes from buildingSMART's final IFC4X3_ADD2 PSD publication:

https://standards.buildingsmart.org/IFC/DEV/IFC4_3/HTML/annex-a-psd.zip

The canonical bSDD identifier form is stored in every index row. IFCtoLBD uses
the property-set membership as part of property resolution, so names from
custom sets are not mistaken for official IFC concepts. The conversion path
does not access the network.

The source ZIP checksum is
`40d941301828112b4cab778f22a87c0b25bd7a260b36af306159a534299c1ee7`
(SHA-256). Of its 760 XML files, 645 are property/quantity-set definitions;
the compact index contains those 645 class rows plus 3,642 set/property rows.

The formal IFC4.3 publication is licensed under CC BY-ND 4.0. Copyright
1996-2026 buildingSMART International Limited. The upstream notice permits use
in software development and translation into another computer language with
full attribution. See the repository-level `THIRD_PARTY_PROVENANCE.md` for the
source, transformation, trademark notice, and licence links.

Comment lines begin with `#`; data rows are:

```text
C<TAB>set code<TAB><TAB>class URI
P<TAB>set code<TAB>property code<TAB>property URI
```
