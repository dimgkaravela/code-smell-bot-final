// MLCQ cropped selected sample
// Original local source: feature_envy/09_Feature_Envy_replaceSubstitution_String_Pattern_String_boolean.java
// Original target local lines: 299-307
// Crop local lines: 269-337

        String paramNum = match.group(3);
        if (paramNum != null) {
          try {
            int num = Integer.parseInt(paramNum);
            if (num < 0 || num > params.length) {
              throw new BadFormatString("index " + num + " from " + format +
                                        " is outside of the valid range 0 to " +
                                        (params.length - 1));
            }
            result.append(params[num]);
          } catch (NumberFormatException nfe) {
            throw new BadFormatString("bad format in username mapping in " + 
                                      paramNum, nfe);
          }
          
        }
        start = match.end();
      }
      return result.toString();
    }

    /**
     * Replace the matches of the from pattern in the base string with the value
     * of the to string.
     * @param base the string to transform
     * @param from the pattern to look for in the base string
     * @param to the string to replace matches of the pattern with
     * @param repeat whether the substitution should be repeated
     * @return
     */
    static String replaceSubstitution(String base, Pattern from, String to, 
                                      boolean repeat) {
      Matcher match = from.matcher(base);
      if (repeat) {
        return match.replaceAll(to);
      } else {
        return match.replaceFirst(to);
      }
    }

    /**
     * Try to apply this rule to the given name represented as a parameter
     * array.
     * @param params first element is the realm, second and later elements are
     *        are the components of the name "a/b@FOO" -> {"FOO", "a", "b"}
     * @return the short name if this rule applies or null
     * @throws IOException throws if something is wrong with the rules
     */
    String apply(String[] params) throws IOException {
      String result = null;
      if (isDefault) {
        if (defaultRealm.equals(params[0])) {
          result = params[1];
        }
      } else if (params.length - 1 == numOfComponents) {
        String base = replaceParameters(format, params);
        if (match == null || match.matcher(base).matches()) {
          if (fromPattern == null) {
            result = base;
          } else {
            result = replaceSubstitution(base, fromPattern, toPattern,  repeat);
          }
        }
      }
      if (result != null && nonSimplePattern.matcher(result).find()) {
        throw new NoMatchingRule("Non-simple name " + result +
                                 " after auth_to_local rule " + this);
      }
      return result;
